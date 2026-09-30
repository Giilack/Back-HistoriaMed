package com.historiamed.backend.common.security;

import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Cifra el campo al guardarlo y lo descifra al leerlo: el resto del código trabaja con el texto normal. Se aplica
 * con {@code @Convert(converter = CampoCifradoConverter.class)}. Hibernate lo obtiene de Spring, que le inyecta
 * {@link CifradoDatos}.
 */
@Component
@Converter
public class CampoCifradoConverter implements AttributeConverter<String, String> {

	private final CifradoDatos cifrado;

	public CampoCifradoConverter(CifradoDatos cifrado) {
		this.cifrado = cifrado;
	}

	@Override
	public String convertToDatabaseColumn(String valor) {
		return cifrado.cifrar(valor);
	}

	@Override
	public String convertToEntityAttribute(String valor) {
		return cifrado.descifrar(valor);
	}

}
