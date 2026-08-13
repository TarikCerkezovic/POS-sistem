package pos.data;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;

@Converter(autoApply = true)
public class KonverterLocalDateTime implements AttributeConverter<LocalDateTime, String> {

    @Override
    public String convertToDatabaseColumn(LocalDateTime vrijeme) {
        if (vrijeme == null) {
            return null;
        }
        return vrijeme.toString();
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String tekst) {
        if (tekst == null) {
            return null;
        }
        return LocalDateTime.parse(tekst);
    }
}
