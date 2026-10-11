package com.forvmom.MomentForeverBooking.domain.enums;

// Which level of the pricing chain produced the resolvedPricePerPerson.
// CLIENT means the grand total came from the UI-quoted amount, not derivation.
public enum PricingLevel {
    BASE,
    LOCATION,
    SLOT,
    PINCODE,
    CLIENT
}
