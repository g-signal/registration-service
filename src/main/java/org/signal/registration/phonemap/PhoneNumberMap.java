package org.signal.registration.phonemap;

import jakarta.validation.constraints.NotBlank;

public record PhoneNumberMap(@NotBlank String from, @NotBlank String to){

}
