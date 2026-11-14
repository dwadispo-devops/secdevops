package ma.ensa.pharma.prescription.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PrescriptionEstimateDtos {
    private PrescriptionEstimateDtos() {}
    public record Item(@NotNull @Pattern(regexp = "[0-9]{13}") String code,
                       @NotNull @Min(1) @Max(10000) @JsonDeserialize(using = WholeQuantity.class) Integer quantity,
                       boolean unitConfirmed) {}
    /** Aucun arrondi silencieux de 1,5 boîte vers 1 ; limité à cet endpoint. */
    public static class WholeQuantity extends JsonDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT)
                return context.reportInputMismatch(Integer.class, "Quantité entière attendue");
            return parser.getIntValue();
        }
    }
    public record Request(UUID prescriptionId, @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items) {}
    public record Line(String code, String name, String presentation, int quantity, BigDecimal unitPrice,
                       Integer catalogRate, BigDecimal estimatedCost, String reason) {}
    public record Estimate(String currency, String source, Instant calculatedAt, List<Line> lines,
                           boolean complete, BigDecimal knownSubtotal, BigDecimal totalEstimatedCost,
                           BigDecimal reimbursableAmount, BigDecimal remainingCharge,
                           String reimbursementExplanation) {}
}
