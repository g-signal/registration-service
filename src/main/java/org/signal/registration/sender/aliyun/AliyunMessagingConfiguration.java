package org.signal.registration.sender.aliyun;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import javax.annotation.Nullable;

@Context
@ConfigurationProperties("aliyun.messaging")
record AliyunMessagingConfiguration(@NotBlank String regionId,
                                    @NotBlank
                                    String accessKeyId,
                                    @NotBlank
                                    String accessSecret,
                                    @NotBlank
                                    String templateCode,
                                    @NotBlank
                                    String signName,
                                    @Nullable
                                    List<@NotBlank String> supportedLanguages) {

  private static List<String> DEFAULT_SUPPORTED_LANGUAGES = List.of(
      "zh",
      "zh-CN"
  );

  AliyunMessagingConfiguration {
    if (supportedLanguages == null) {
      supportedLanguages = DEFAULT_SUPPORTED_LANGUAGES;
    }
  }
}
