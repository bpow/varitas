package org.drpowell.util;

import java.util.BitSet;

/**
 * This class is like URLEncoder/URLDecoder, but allows customization of the characters to encode.
 * 
 * Note that the [recode/passThrough]AdditionalCharacters methods return new objects.
 * 
 * WARNING: this only handles ASCII, because that is all I need at the time of writing this.
 * 
 * @author Bradford Powell
 *
 */
public class CustomPercentEncoder {
	private final BitSet leaveAlone;
	private final boolean plusSpaces;
	
	public static final char [] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
	
	protected CustomPercentEncoder(BitSet charsToLeaveAlone, boolean convertSpacesToPlus) {
		leaveAlone = charsToLeaveAlone;
		plusSpaces = convertSpacesToPlus;

		// sanity checks
		if (plusSpaces) {
			leaveAlone.set(' '); // space characters will be handled differently...
			leaveAlone.clear('+'); // must encode plus characters if replacing spaces with plus
		}
		
		leaveAlone.clear('%'); // have to encode the escape character!
	}
	
	public static CustomPercentEncoder xFormURLEncoder() {
		return allowAlphanumeric().recodeAdditionalCharacters("-_.!~*'()".toCharArray());
	}
	
	public static CustomPercentEncoder allowAsciiPrintable(boolean convertSpacesToPlus) {
		BitSet leaveAlone = new BitSet(256);
		leaveAlone.set(32, 126);
		return new CustomPercentEncoder(leaveAlone, convertSpacesToPlus);
	}
	
	public static CustomPercentEncoder allowAlphanumeric() {
		BitSet leaveAlone = new BitSet(256);
		// alpha
		leaveAlone.set('a', 'z');
		leaveAlone.set('A', 'Z');
		// numeric
		leaveAlone.set('0', '9');

		return new CustomPercentEncoder(leaveAlone, true);
	}
	
	public static CustomPercentEncoder recodeAFewChars(char [] charsToRecode, boolean convertSpaceToPlus) {
		BitSet leaveAlone = new BitSet(256);
		for (char c: charsToRecode) {
			leaveAlone.set(c);
		}
		return new CustomPercentEncoder(leaveAlone, convertSpaceToPlus);
	}

	public CustomPercentEncoder passThroughAdditionalCharacters(char [] charsToPass) {
		BitSet newLeaveAlone = (BitSet) leaveAlone.clone();
		for (char c : charsToPass) {
			newLeaveAlone.clear(c);
		}
		return new CustomPercentEncoder(newLeaveAlone, plusSpaces);
		
	}
	
	public CustomPercentEncoder recodeAdditionalCharacters(char [] charsToRecode) {
		BitSet newLeaveAlone = (BitSet) leaveAlone.clone();
		for (char c : charsToRecode) {
			newLeaveAlone.clear(c);
		}
		return new CustomPercentEncoder(newLeaveAlone, plusSpaces);
	}
	
	private static final StringBuilder appendEncodedChar(StringBuilder sb, char c) {
		if (c <= 0x007f) {
			sb.append('%').append(HEX_DIGITS[(c>>4)&0xf]).append(HEX_DIGITS[c&0xf]);
		} else {
			throw new UnsupportedOperationException("Tried to URLEncode a non-ASCII character: " + c);
		}
		return sb;
	}

	public String encode(String in) {
		StringBuilder sb = null;
		int i = 0;
		while (i < in.length()) {
			char c = in.charAt(i);
			if ((c == ' ' && plusSpaces) || !leaveAlone.get(c)) {
				sb = new StringBuilder(in.length()*2);
				sb.append(in.substring(0, i));
				break;
			}
			i++;
		}
		if (null == sb) return in; // we did not reach anything that needed to be changed
		while (i < in.length()) {
			char c = in.charAt(i);
			if (c == ' ' && plusSpaces) {
				sb.append('+');
			} else if (!leaveAlone.get(c)) {
				appendEncodedChar(sb, c);
			} else {
				sb.append(c);
			}
			i++;
		}
		return sb.toString();
	}
	
	public String decode(String encoded) {
		StringBuilder sb = null;
		int i = 0;
		while (i < encoded.length()) {
			char c = encoded.charAt(i);
			if (c == '+' && plusSpaces || c == '%') {
				sb = new StringBuilder(encoded.length());
				sb.append(encoded.substring(0, i));
				break;
			}
			i++;
		}
		if (null == sb) return encoded; // nothing to change
		while (i < encoded.length()) {
			char c = encoded.charAt(i);
			if (c == '+' && plusSpaces) {
				sb.append(' ');
				i++;
			} else if (c == '%') {
				sb.append((char) Integer.parseInt(encoded.substring(i+1,i+3),16));
				i += 3;
			} else {
				sb.append(c);
				i++;
			}
		}
		return sb.toString();
	}

}
