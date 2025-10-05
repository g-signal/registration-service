package org.signal.registration.sender.aliyun;

import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.signal.registration.metrics.MetricsUtil;
import org.signal.registration.sender.*;
import org.signal.registration.sender.twilio.ApiExceptions;
import org.signal.registration.util.CompletionExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Singleton
public class AliyunVerifySender implements VerificationCodeSender {

  public static final String SENDER_NAME = "aliyun-verify";
  private static final Logger logger = LoggerFactory.getLogger(AliyunVerifySender.class);
  private static final String redis_key = "registration-service:aliyun-verify:";
  private static final String INVALID_PARAM_NAME = MetricsUtil.name(AliyunVerifySender.class, "invalidParam");
  private static ExecutorService executorService = null;
  private final MeterRegistry meterRegistry;
  private final ApiClientInstrumenter apiClientInstrumenter;
  private final AliyunMessagingConfiguration configuration;
  private final StatefulRedisConnection<String, String> connection;
  private final Duration minRetryWait;
  private final int maxRetries;

  public AliyunVerifySender(final MeterRegistry meterRegistry,
      final ApiClientInstrumenter apiClientInstrumenter,
      final AliyunMessagingConfiguration configuration,
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
      synchronized (AliyunVerifySender.class) {
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
    return withRetries(() -> {
      CompletableFuture<SendSmsResponse> completableFuture =
          CompletableFuture.supplyAsync(() -> {
            SendSmsResponse sendSmsResponse = null;
            try {
              sendSmsResponse = AliyunSmsUtil.send(this.configuration.regionId(),
                  this.configuration.accessKeyId(),
                  this.configuration.accessSecret(),
                  this.configuration.templateCode(),
                  this.configuration.signName(),
                  nationalNumber,
                  tmpCode);
            } catch (ClientException e) {
              throw new RuntimeException(e);
            }
            return sendSmsResponse;
          }, AliyunVerifySender.getExecutorService());
      return completableFuture;
    }, endpointName)
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
          if (throwable == null && (StringUtils.equalsIgnoreCase(sendSmsResponse.getCode(), "ok")
              || StringUtils.equalsIgnoreCase(sendSmsResponse.getCode(), "0"))) {
            SetArgs setArgs = SetArgs.Builder.ex(getAttemptTtl());
            connection.sync().set(redis_key + sendSmsResponse.getRequestId(), tmpCode, setArgs);
            return new AttemptData(Optional.ofNullable(sendSmsResponse.getRequestId()),
                sendSmsResponse.getRequestId().getBytes(StandardCharsets.UTF_8));
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
  }

  @Override
  public CompletableFuture<Boolean> checkVerificationCode(final String verificationCode, final byte[] senderData) {
    String requestId = new String(senderData, StandardCharsets.UTF_8);

    String tmpCode = connection.sync().get(redis_key + requestId);

    return CompletableFuture.completedFuture(StringUtils.equals(verificationCode, tmpCode));
  }

  <T> CompletionStage<T> withRetries(
      final Supplier<CompletionStage<T>> supp,
      final String endpointName) {
    return Mono.defer(() -> Mono.fromCompletionStage(supp))
        .retryWhen(Retry
            .backoff(maxRetries, minRetryWait)
            .filter(ApiExceptions::isRetriable)
            .doBeforeRetry(retrySignal ->
                this.apiClientInstrumenter.recordApiRetry(
                    this.getName(),
                    endpointName,
                    ApiExceptions.extractErrorCode(retrySignal.failure()))))
        .onErrorMap(Exceptions::isRetryExhausted, Throwable::getCause)
        .toFuture();
  }
}
