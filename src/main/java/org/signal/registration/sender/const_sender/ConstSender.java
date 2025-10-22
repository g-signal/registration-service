package org.signal.registration.sender.const_sender;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.signal.registration.sender.AttemptData;
import org.signal.registration.sender.ClientType;
import org.signal.registration.sender.MessageTransport;
import org.signal.registration.sender.SenderRejectedRequestException;
import org.signal.registration.sender.UnsupportedMessageTransportException;
import org.signal.registration.sender.VerificationCodeSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ConstSender implements VerificationCodeSender {

  public static final String SENDER_NAME = "const-verify";
  private static final Logger logger = LoggerFactory.getLogger(ConstSender.class);

  Map<Phonenumber.PhoneNumber, String> map = new HashMap<>();
  private Map<String, String> codeMap = new HashMap<>();

  public ConstSender(final ConstSenderConfiguration configuration) throws NumberParseException {
    if(configuration.phoneNumbers()!=null){
      for(String ph : configuration.phoneNumbers()){
        String[] splits = ph.split("_");
        Phonenumber.PhoneNumber p1 = new Phonenumber.PhoneNumber();
        p1.setCountryCode(Integer.parseInt(splits[0]));
        p1.setNationalNumber(Long.parseLong(splits[1]));
        map.put(p1, splits[2]);

        logger.info("const:" + PhoneNumberUtil.getInstance().format(p1, PhoneNumberUtil.PhoneNumberFormat.E164));

      }
    }



    logger.info("init:"+this.getName());
  }

  public boolean hasNumber(Phonenumber.PhoneNumber p1){
    return this.map.get(p1)!=null;
  }

  public boolean hasRequest(final byte[] senderData){
    String requestId = new String(senderData, StandardCharsets.UTF_8);
    return codeMap.get(requestId)!=null;
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
    return true;
  }

  @Override
  public CompletableFuture<AttemptData> sendVerificationCode(final MessageTransport messageTransport,
      final Phonenumber.PhoneNumber phoneNumber, final List<Locale.LanguageRange> languageRanges,
      final ClientType clientType) throws UnsupportedMessageTransportException {
    final String verificationCode = map.get(phoneNumber);

    String sessionId = UUID.randomUUID().toString();
    codeMap.put(sessionId, verificationCode);
    if (StringUtils.isNotBlank(verificationCode)) {
      AttemptData attemptData = new AttemptData(Optional.ofNullable(sessionId),
          sessionId.getBytes(StandardCharsets.UTF_8));

      return CompletableFuture.completedFuture(attemptData);
    } else {
      return CompletableFuture.failedFuture(new SenderRejectedRequestException("Unsupported phone number"));
    }
  }

  @Override
  public CompletableFuture<Boolean> checkVerificationCode(final String verificationCode, final byte[] senderData) {
    String requestId = new String(senderData, StandardCharsets.UTF_8);

    //String tmpCode = connection.sync().get(redis_key + requestId);
    String tmpCode = codeMap.get(requestId);
    return CompletableFuture.completedFuture(StringUtils.equals(verificationCode, tmpCode));
  }
}
