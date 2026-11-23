package ma.medidispo.pharmacy_service.rating;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class PharmacyRatingDtos {
    private PharmacyRatingDtos() {}
    public record CreateRating(@NotNull @Min(1) @Max(5)
                               @JsonDeserialize(using = WholeStars.class) Integer stars) {}
    public record Rating(UUID id, UUID reservationId, int stars, OffsetDateTime createdAt) {}

    public static class WholeStars extends ValueDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT)
                return context.reportInputMismatch(Integer.class, "Une note entière est attendue");
            return parser.getIntValue();
        }
    }
}
