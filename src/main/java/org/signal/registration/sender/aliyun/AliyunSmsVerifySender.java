package org.signal.registration.sender.aliyun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import io.lettuce.core.SetArgs;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.signal.registration.metrics.MetricsUtil;
import org.signal.registration.sender.ApiClientInstrumenter;
import org.signal.registration.sender.VerificationCodeSender;
import org.signal.registration.util.CompletionExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.signal.registration.sender.*;
import org.signal.registration.sender.twilio.ApiExceptions;

@Singleton
public class AliyunSmsVerifySender implements VerificationCodeSender {

  public static final String SENDER_NAME = "aliyunsms-verify";
  private static final Logger logger = LoggerFactory.getLogger(AliyunSmsVerifySender.class);
  private static final String redis_key = "registration-service:aliyunsms-verify:";
  private static final String INVALID_PARAM_NAME = MetricsUtil.name(AliyunSmsVerifySender.class, "invalidParam");
  private static ExecutorService executorService = null;
  private final MeterRegistry meterRegistry;
  private final ApiClientInstrumenter apiClientInstrumenter;
  private final AliyunSmsMessagingConfiguration configuration;
  private final VerificationCodeGenerator verificationCodeGenerator;

  private final Duration minRetryWait;
  private final int maxRetries;

  private Map<String, String>  codeMap = new HashMap<>();

  public AliyunSmsVerifySender(final MeterRegistry meterRegistry,
                               final ApiClientInstrumenter apiClientInstrumenter,
                               final AliyunSmsMessagingConfiguration configuration,
      final VerificationCodeGenerator verificationCodeGenerator, final @Value("${aliyunsms.min-retry-wait:100ms}") Duration minRetryWait,
                               final @Value("${aliyunsms.max-retries:5}") int maxRetries) {
    this.meterRegistry = meterRegistry;
    this.apiClientInstrumenter = apiClientInstrumenter;
    this.configuration = configuration;
    this.verificationCodeGenerator = verificationCodeGenerator;
    this.minRetryWait = minRetryWait;
    this.maxRetries = maxRetries;
  }

  public static ExecutorService getExecutorService() {
    if (executorService == null) {
      synchronized (AliyunSmsVerifySender.class) {
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

    String tmpCode = verificationCodeGenerator.generateVerificationCode();
    String nationalNumber = String.valueOf(phoneNumber.getNationalNumber());

    CompletableFuture<AttemptData> completableFuture = CompletableFuture.supplyAsync(() -> {
          return AliyunSmsUtil.sendCode(this.configuration.accessKeyId(),
              this.configuration.accessSecret(),
              this.configuration.templateCode(),
              this.configuration.templateParamKey(),
              this.configuration.signName(),
              nationalNumber,
              tmpCode
          );
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
          String aliyunRequestId = sendSmsResponse;
          if (throwable == null && aliyunRequestId !=null) {
            SetArgs setArgs = SetArgs.Builder.ex(getAttemptTtl());
            codeMap.put(redis_key + aliyunRequestId,tmpCode );
            //connection.sync().set(redis_key + waySmsRes.getData(), tmpCode, setArgs);
            return new AttemptData(Optional.ofNullable(aliyunRequestId),
                aliyunRequestId.getBytes(StandardCharsets.UTF_8));
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

    //String tmpCode = connection.sync().get(redis_key + requestId);
    String tmpCode = codeMap.get(redis_key + requestId);
    return CompletableFuture.completedFuture(StringUtils.equals(verificationCode, tmpCode));
  }
}
