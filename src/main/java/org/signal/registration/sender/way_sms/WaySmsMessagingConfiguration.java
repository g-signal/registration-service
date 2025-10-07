package org.signal.registration.sender.way_sms;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import javax.annotation.Nullable;
import java.util.List;

@Context
@ConfigurationProperties("waysms.messaging")
record WaySmsMessagingConfiguration(@NotBlank String url,
                                    @NotBlank
                                    String apiAccount,
                                    @NotBlank
                                    String secretKey,
                                    @NotBlank
                                    String template,
                                    @NotNull
                                    Integer codeLen,
                                    @Nullable
                                    List<@NotBlank String> supportedLanguages) {

  private static List<String> DEFAULT_SUPPORTED_LANGUAGES = List.of(
      "zh",
      "zh-CN"
  );

  WaySmsMessagingConfiguration {
    if (supportedLanguages == null) {
      supportedLanguages = DEFAULT_SUPPORTED_LANGUAGES;
    }
  }
}
