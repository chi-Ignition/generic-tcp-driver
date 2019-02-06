/*******************************************************************************
 * Copyright 2012-2019 C. Hiesserich
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.chitek.ignition.drivers.generictcp.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.util.Formatter;
import java.util.StringTokenizer;

import org.eclipse.milo.opcua.stack.core.BuiltinDataType;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;

import com.inductiveautomation.xopc.driver.util.ByteUtilities;

import java.util.Map;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;


public class Util {
	
	/**
	 * Converts a Hex String to byte[]. String delimiters are " ,;".
	 * "A0, 10, 20" = {a0, 10,20}
	 * 
	 * @param input
	 * @return
	 * @throws NumberFormatException
	 */
	public static byte[] hexString2ByteArray(String input) throws ParseException {
		return hexString2ByteArray(input, null, ByteOrder.BIG_ENDIAN);
	}
	
	/**
	 * Converts a Hex String to byte[]. String delimiters are " ,;".
	 * "A0, 10, 20" = {a0, 10,20}
	 * 
	 * 
	 * @param input
	 * @param values
	 * 	A map containing replacement keys with a value
	 * @param order
	 * 	The ByteOrder used for value replacements
	 * @return
	 * @throws ParseException
	 */
	public static byte[] hexString2ByteArray(String input, Map<String, Number> values, ByteOrder order ) throws ParseException
	{
		StringTokenizer tokenizer = new StringTokenizer(input, " ,;");
		
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                       
		int position=0;
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken().trim();
			if (values != null && values.containsKey(token)) {
				Number val=values.get(token);
				if ( val != null && val instanceof Integer) {
					try {
						buffer.write(ByteUtilities.get(order).fromInt(val.intValue()));
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else if ( val != null && val instanceof Short) {
					try {
						buffer.write(ByteUtilities.get(order).fromShort(val.shortValue()));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				else if ( val != null && val instanceof Byte) {
					buffer.write((Byte)val);
				}
			} else {
				try {
					int iValue = Integer.decode(token);
					if (iValue < -128 || iValue > 255)
						throw new NumberFormatException();
					buffer.write(iValue);
				} catch (NumberFormatException ex) {
					throw new ParseException(String.format("Invalid token '%s'", token), position);
				}
			}
			position ++;
		}		  
		
		return buffer.toByteArray();
  	}
	
	public static String byteArray2HexString(byte[] buf) {
		return byteArray2HexString(buf, ',');
	}
	
	public static String byteArray2HexString(byte[] buf, char delimiter) {
		StringBuilder sb = new StringBuilder();
		Formatter f = new Formatter(sb);

		if (buf != null) {
			for (int i = 0; i < buf.length; ++i) {
				if (i>0)
					f.format(",0x%02X", Byte.valueOf(buf[i]) );
				else
					f.format("0x%02X", Byte.valueOf(buf[i]) );
			}
		}
		f.close();
		return sb.toString();
	}
	
	/**
	 * Try to wrap the given Object to an Variant with the selected DataType. An Exception
	 * is thrown if the DataType is not supported or if the value does not match the DataType.
	 * 
	 * @param value
	 * @param dataType
	 * @return
	 */
	public static Variant makeVariant(Object value, BuiltinDataType dataType) {
		if (value instanceof String)
			if (((String)value).isEmpty()) 
				value = null;
			else if(isNumericDataType(dataType))
				try {
					value = Long.parseLong((String)value);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException(String.format("Cannot coerce String '%s' to DataType %s",
						value, dataType.name() ));
				}
		if (isNumericDataType(dataType)) {
			if (value==null) 
				return makeVariant(0, dataType);
			else
				if (value instanceof Number) 
					return makeVariant(((Number)value).longValue(), dataType);
				else
					throw new IllegalArgumentException(String.format("Cannot coerce %s to DataType %s",
						value.getClass().getSimpleName(), dataType.name() ));
		} else {
			switch (dataType) {
			case String:
				if (value==null)
					return new Variant("");
				if (value instanceof String) 
					return new Variant((String)value);
				else
					throw new IllegalArgumentException(String.format("Cannot coerce %s to DataType %s",
						value.getClass().getSimpleName(), dataType.name() ));
			default:
				break;
			}
		}
		throw new IllegalArgumentException(String.format("Unsupported DataType %s", dataType.name() ));
	}
	
	/**
	 * Wraps the given numeric value in a Variant with the selected DataType. An Exception is thrown, if
	 * value does not fit in the DataType.
	 * 
	 * @param value
	 * @param dataType
	 * @return
	 * 		A new Variant wrapping the dataType
	 */
	public static Variant makeVariant(long value, BuiltinDataType dataType) {
		switch (dataType) {
		case Byte:
			if (value<0)
				throw new IllegalArgumentException("Cannot set value of UByte to a number smaller than 0."); 
			if (value>255)
				throw new IllegalArgumentException("Cannot set value of UByte to a number larger than 255.");
			UByte bVal = ubyte((byte) value);
			return new Variant(bVal);
		case UInt16:
			return new Variant(ushort((int)value));
		case Int16:
			return new Variant((short)value);
		case UInt32:
			return new Variant(uint(value));
		case Int32:
			return new Variant((int)value);
		default:
			throw new IllegalArgumentException(String.format("Unsupported DataType %s", dataType.name() ));	
		}
	}
	
	public static boolean isNumericDataType(BuiltinDataType dataType) {
		switch (dataType) {
		case Byte:
		case Double:
		case Float:
		case Int16:
		case Int32:
		case Int64:
		case UInt16:
		case UInt32:
		case UInt64:
			return true;
		default:
			return false;
		}
		
	}
}
