/*
 * Logback GELF - zero dependencies Logback GELF appender library.
 * Copyright (C) 2016 Oliver Siegmar
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package de.siegmar.logbackgelf.mappers;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.Marker;

import java.util.Iterator;
import java.util.Optional;

public class MarkerFieldMapper extends AbstractFixedNameFieldMapper<String> {

	public MarkerFieldMapper (String fieldName) {
		super(fieldName);
	}

	@Override
	protected Optional<String> getValue (ILoggingEvent event) {
		return Optional.ofNullable(event.getMarker())
			.map(MarkerFieldMapper::buildMarkerStr);
	}

	private static String buildMarkerStr (Marker marker) {
		if (!marker.hasReferences()){
			return marker.getName();
		}

		StringBuilder sb = new StringBuilder(marker.getName());

		Iterator<Marker> it = marker.iterator();
		do {
			sb.append(',').append(it.next().getName());
		} while (it.hasNext());

		return sb.toString();
	}

}