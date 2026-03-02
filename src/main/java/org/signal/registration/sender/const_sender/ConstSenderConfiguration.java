package org.signal.registration.sender.const_sender;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import javax.annotation.Nullable;
import java.util.List;

@Context
@ConfigurationProperties("const-verify")
record ConstSenderConfiguration(@Nullable List<@NotBlank String> phoneNumbers) {
}
