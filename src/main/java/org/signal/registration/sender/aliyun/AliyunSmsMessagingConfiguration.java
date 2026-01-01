package org.signal.registration.sender.aliyun;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import javax.annotation.Nullable;
import java.util.List;

@Context
@ConfigurationProperties("aliyunsms.messaging")
record AliyunSmsMessagingConfiguration(@NotBlank String accessKeyId,
                                       @NotBlank String accessSecret,
                                       @NotBlank String templateCode,
                                       @NotNull String templateParamKey,
                                       @NotBlank String signName,
                                       @NotNull Integer codeLen,
                                       @Nullable List<@NotBlank String> supportedLanguages) {

  private static List<String> DEFAULT_SUPPORTED_LANGUAGES = List.of(
      "zh",
      "zh-CN"
  );

  AliyunSmsMessagingConfiguration {
    if (supportedLanguages == null) {
      supportedLanguages = DEFAULT_SUPPORTED_LANGUAGES;
    }
  }
}
