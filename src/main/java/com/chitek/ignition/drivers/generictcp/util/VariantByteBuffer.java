/*******************************************************************************
 * Copyright 2012-2013 C. Hiesserich
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import com.inductiveautomation.opcua.types.DataType;
import com.inductiveautomation.opcua.types.UByte;
import com.inductiveautomation.opcua.types.UInt16;
import com.inductiveautomation.opcua.types.UInt32;
import com.inductiveautomation.opcua.types.Variant;

/**
 * This class wraps a ByteBuffer and adds methods to read the buffers content as
 * Variant.
 * All Methods (except readString) use a size argument to specify the size of the array to Read.
 * When passing a size <= 1, the returned Variant will be scalar, if size is > 1 the Variant
 * will contain an array.
 * 
 * @author chi
 *
 */
public class VariantByteBuffer  {

	private final ByteBuffer buffer;

	public VariantByteBuffer(byte[] array) {
		this.buffer = ByteBuffer.wrap(array);
	}

	/**
	 * Modifies the byte order of the underlying ByteBuffer.
	 * 
	 * @param bo
	 * 		The new byte order, either {@link ByteOrder#BIG_ENDIAN} or {link ByteOrder#LITTLE_ENDIAN}
	 * @return
	 * 	This buffer
	 */
	public VariantByteBuffer order(ByteOrder bo) {
		buffer.order(bo);
		return this;
	}

	/**
	 * Returns the number of bytes between the current position and the limit.
	 *
	 * @return
	 * 	The number of bytes remaining in this buffer
	 */
	public int remaining() {
		return buffer.remaining();
	}

	public byte get() {
		return buffer.get();
	}

	/**
	 * Relative bulk <i>get</i> method.
	 *
	 * <p> This method transfers bytes from this buffer into the given
	 * destination array.  An invocation of this method of the form
	 * <tt>src.get(a)</tt> behaves in exactly the same way as the invocation
	 *
	 * <pre>
	 *     src.get(a, 0, a.length) </pre>
	 */
	public void get(byte[] dst) {
		buffer.get(dst);
	}

	public short getShort() {
		return buffer.getShort();
	}

	public int getInt() {
		return buffer.getInt();
	}

	public long getLong() {
		return buffer.getLong();
	}

	/**
	 * Returns this buffer's position.
	 * @return
	 *  The position of this buffer
	 */
	public int position() {
		return buffer.position();
	}

	/**
	 * Sets this buffer's position. If the mark is defined and larger than the new position then it is discarded.
	 * 
	 * @param newPosition
	 *  The new position value; must be non-negative and no larger than the current limit
	 */
	public void position(int newPosition) {
		buffer.position(newPosition);
	}

	/**
	 * 
	 * @param size
	 * 		The size of the array to read. size <=1 return a Scalar Variant.
	 * @return
	 */
	public Variant readByte(int size) {

		if (size <= 1)
			return new Variant(buffer.get());

		Byte[] value = new Byte[size];
		for (int i = 0; i < size; i++) {
			value[i] = buffer.get();
		}

		return new Variant(value, DataType.Byte);

	}

	public Variant readUByte(int size) {

		if (size <= 1)
			return new Variant(new UByte(buffer.get()) );

		UByte[] value = new UByte[size];
		for (int i = 0; i < size; i++) {
			value[i] = new UByte(buffer.get());
		}

		return new Variant(value);

	}

	/**
	 * Reads an array of boolean values. If size is > 1 the returned array will have a length
	 * of size*8. Ignition does not support multi-dimensional arrays.
	 * 
	 * @param size
	 * @return
	 */
	public Variant readBool8(int size) {

		if (size <= 1)
			return new Variant(Byte2BitArray(buffer.get()));

		Boolean[] value = new Boolean[8*size];
		for (int i=0; i < size; i++) {
			System.arraycopy(Byte2BitArray(buffer.get()), 0, value, 8*i, 8);
		}

		return new Variant(value);
	}

	/**
	 * Same as raedBool8, but returns an array with length 16*size
	 * 
	 * @param size
	 * @return
	 */
	public Variant readBool16(int size) {

		if (size <= 1)
			return new Variant(Short2BitArray(buffer.getShort()));

		Boolean[] value = new Boolean[16*size];
		for (int i=0; i < size; i++) {
			System.arraycopy(Short2BitArray(buffer.getShort()), 0, value, 16*i, 16);
		}

		return new Variant(value);
	}

	public Variant readUInt16(int size) {

		if (size <= 1)
			return new Variant(new UInt16(buffer.getShort() & 0xffff));

		UInt16[] value = new UInt16[size];
		for (int i = 0; i < size; i++) {
			value[i] = new UInt16(buffer.getShort() & 0xffff);
		}

		return new Variant(value);
	}

	public Variant readInt16(int size) {

		if (size <= 1)
			return new Variant(buffer.getShort());

		Short[] value = new Short[size];
		for (int i = 0; i < size; i++) {
			value[i] = buffer.getShort();
		}

		return new Variant(value);
	}

	public Variant readUInt32(int size) {

		UInt32[] value = new UInt32[size];
		for (int i = 0; i < size; i++) {
			value[i] = new UInt32(buffer.getInt() & 0xffffffff);
		}

		if (size > 1)
			return new Variant(value);
		else
			return new Variant(value[0]);
	}

	public Variant readInt32(int size) {

		if (size <= 1)
			return new Variant(buffer.getInt());

		Integer[] value = new Integer[size];
		for (int i = 0; i < size; i++) {
			value[i] = buffer.getInt();
		}

		return new Variant(value);
	}

	public Variant readFloat(int size) {

		if (size <= 1)
			return new Variant(buffer.getFloat());

		Float[] value = new Float[size];
		for (int i = 0; i < size; i++) {
			value[i] = buffer.getFloat();
		}

		return new Variant(value);
	}

	/**
	 * This method returns a String with the given length. Arrays of String are not supported.
	 * 
	 * @param length
	 * @return
	 */
	public Variant readString(int length) {

		byte[] stringbuffer = new byte[length];
		buffer.get(stringbuffer);

		CharsetDecoder decoder = Charset.forName( "ISO-8859-1").newDecoder();
		decoder.onMalformedInput(CodingErrorAction.IGNORE);
		decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);

		String value;
		try {
			CharBuffer chars = decoder.decode(ByteBuffer.wrap(stringbuffer));
			value = chars.toString().trim();
		} catch (CharacterCodingException e) {
			value = "";
		}


		return new Variant(value);
	}

	private static Boolean[] Byte2BitArray(byte b) {

		Boolean[] boolArray = new Boolean[8];
		for (int i = 0; i < 8; i++) {
			boolArray[i]=( (b & (1 << i) ) > 0);
		}

		return boolArray;
	}

	private static Boolean[] Short2BitArray(short s) {

		Boolean[] boolArray = new Boolean[16];

		for (int i = 0; i < 16; i++) {
			boolArray[i]=( (s & (1 << i) ) > 0);
		}

		return boolArray;
	}
}
