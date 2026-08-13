package pos.data;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

@Converter(autoApply = true)
public class KonverterLocalDate implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate datum) {
        if (datum == null) {
            return null;
        }
        return datum.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String tekst) {
        if (tekst == null) {
            return null;
        }
        return LocalDate.parse(tekst);
    }
}
