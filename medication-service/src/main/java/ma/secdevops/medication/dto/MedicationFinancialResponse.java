package ma.secdevops.medication.dto;

import java.math.BigDecimal;

/** Données du catalogue, sans interpréter le taux comme un droit personnel. */
public record MedicationFinancialResponse(String code, String nom, String presentation,
                                        BigDecimal ppv, Integer tauxRemboursement) {}
