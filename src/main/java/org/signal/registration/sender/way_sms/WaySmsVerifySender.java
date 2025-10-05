package org.signal.registration.sender.way_sms;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import okhttp3.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.signal.registration.metrics.MetricsUtil;
import org.signal.registration.sender.*;
import org.signal.registration.sender.twilio.ApiExceptions;
import org.signal.registration.util.CompletionExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class WaySmsVerifySender implements VerificationCodeSender {

  public static final String SENDER_NAME = "waysms-verify";
  private static final Logger logger = LoggerFactory.getLogger(WaySmsVerifySender.class);
  private static final String redis_key = "registration-service:waysms-verify:";
  private static final String INVALID_PARAM_NAME = MetricsUtil.name(WaySmsVerifySender.class, "invalidParam");
  private static ExecutorService executorService = null;
  private final MeterRegistry meterRegistry;
  private final ApiClientInstrumenter apiClientInstrumenter;
  private final WaySmsMessagingConfiguration configuration;
  private final StatefulRedisConnection<String, String> connection;
  private final Duration minRetryWait;
  private final int maxRetries;

  public WaySmsVerifySender(final MeterRegistry meterRegistry,
      final ApiClientInstrumenter apiClientInstrumenter,
      final WaySmsMessagingConfiguration configuration,
      final StatefulRedisConnection<String, String> connection,
      final @Value("${aliyun.min-retry-wait:100ms}") Duration minRetryWait,
      final @Value("${aliyun.max-retries:5}") int maxRetries) {
    this.meterRegistry = meterRegistry;
    this.apiClientInstrumenter = apiClientInstrumenter;
    this.configuration = configuration;
    this.connection = connection;
    this.minRetryWait = minRetryWait;
    this.maxRetries = maxRetries;
  }

  public static ExecutorService getExecutorService() {
    if (executorService == null) {
      synchronized (WaySmsVerifySender.class) {
        if (executorService == null) {
          executorService = Executors.newCachedThreadPool();
        }
      }
    }

    return executorService;
  }

  @Override
  public String getName() {
    return SENDER_NAME;
  }

  @Override
  public Duration getAttemptTtl() {
    return Duration.ofMinutes(10);
  }

  @Override
  public boolean supportsTransport(final MessageTransport transport) {
    return transport == MessageTransport.SMS;
  }

  @Override
  public boolean supportsLanguage(final MessageTransport messageTransport, final Phonenumber.PhoneNumber phoneNumber,
      final List<Locale.LanguageRange> languageRanges) {
    return Locale.lookupTag(languageRanges, configuration.supportedLanguages()) != null;
  }

  @Override
  public CompletableFuture<AttemptData> sendVerificationCode(final MessageTransport messageTransport,
      final Phonenumber.PhoneNumber phoneNumber, final List<Locale.LanguageRange> languageRanges,
      final ClientType clientType) throws UnsupportedMessageTransportException {
    String locale = Locale.lookupTag(languageRanges, configuration.supportedLanguages());

    final Timer.Sample sample = Timer.start();
    final String endpointName = "verification." + messageTransport.name().toLowerCase() + ".create";

    String tmpCode = RandomStringUtils.secure().nextNumeric(4);
    String nationalNumber = String.valueOf(phoneNumber.getNationalNumber());

    CompletableFuture<AttemptData> completableFuture = CompletableFuture.supplyAsync(() -> {
          HashMap<String, Object> map = new HashMap<>();
          map.put("apiAccount", this.configuration.apiAccount());
          map.put("secretKey", this.configuration.secretKey());
          map.put("mobiles", nationalNumber);
          map.put("content", StringUtils.replace(configuration.template(), "${code}", tmpCode));
          try {
            return post(configuration.url(), map);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

        }, getExecutorService())
        .toCompletableFuture()
        .whenComplete((sessionData, throwable) ->
            this.apiClientInstrumenter.recordApiCallMetrics(
                this.getName(),
                endpointName,
                throwable == null,
                ApiExceptions.extractErrorCode(throwable),
                sample)
        )
        .handle((sendSmsResponse, throwable) -> {
          //{"status":0,"message":"提交成功","data":"c47a4a49-fdba-41ab-8c56-6b773fe2350b","desc":null}
          Gson gson = new GsonBuilder().create();
          WaySmsRes waySmsRes = gson.fromJson(sendSmsResponse, WaySmsRes.class);
          if (throwable == null && waySmsRes.getStatus() == 0) {
            SetArgs setArgs = SetArgs.Builder.ex(getAttemptTtl());
            connection.sync().set(redis_key + waySmsRes.getData(), tmpCode, setArgs);
            return new AttemptData(Optional.ofNullable(waySmsRes.getData()),
                waySmsRes.getData().getBytes(StandardCharsets.UTF_8));
          }

          final Throwable exception = ApiExceptions.toSenderException(throwable);
          if (exception instanceof SenderInvalidParametersException p) {
            final String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForNumber(phoneNumber);
            final Tags tags = p.getParamName().map(param -> switch (param) {
                  case NUMBER -> Tags.of("paramType", "number",
                      MetricsUtil.TRANSPORT_TAG_NAME, messageTransport.name(),
                      MetricsUtil.REGION_CODE_TAG_NAME, StringUtils.defaultIfBlank(regionCode, "XX"));
                  case LOCALE -> Tags.of("paramType", "locale",
                      "locale", locale,
                      MetricsUtil.TRANSPORT_TAG_NAME, messageTransport.name());
                })
                .orElse(Tags.of("paramType", "unknown"));
            meterRegistry.counter(INVALID_PARAM_NAME, tags).increment();
          }
          throw CompletionExceptions.wrap(exception);
        });
    return completableFuture;
  }

  public CompletableFuture<Boolean> checkVerificationCode(final String verificationCode, final byte[] senderData) {
    String requestId = new String(senderData, StandardCharsets.UTF_8);

    String tmpCode = connection.sync().get(redis_key + requestId);

    return CompletableFuture.completedFuture(StringUtils.equals(verificationCode, tmpCode));
  }

  private String post(String url, HashMap<String, Object> map) throws IOException {
    OkHttpClient client = new OkHttpClient();
    // 创建一个MultipartBody.Builder对象
    MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      // 添加文本字段
      builder.addFormDataPart(entry.getKey(), entry.getValue().toString());
    }

    // 构建请求体
    RequestBody requestBody = builder.build();

    // 创建Request对象
    Request request = new Request.Builder()
        .url(url)
        .post(requestBody)
        .build();

    // 发送请求并获取响应
    try (Response response = client.newCall(request).execute()) {
      // 返回响应体内容作为字符串
      return response.body().string();
    }

  }
}
