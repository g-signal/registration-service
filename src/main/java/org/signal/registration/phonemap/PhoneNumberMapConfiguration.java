package org.signal.registration.phonemap;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import jakarta.validation.constraints.NotBlank;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@Context
@ConfigurationProperties("phone-number-map")
public record PhoneNumberMapConfiguration(@Nullable List<PhoneNumberMap> mappings) {
}
