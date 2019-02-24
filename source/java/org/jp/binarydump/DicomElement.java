package org.jp.binarydump;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DicomElement {

	static String[] vrs = new String[] { "OB", "OW", "OF", "SQ", "UT", "UN", "OD", "OL", "UC", "UR" };

	public int tag;
	public int tagAdrs;
	public int tagLen;
	public String vr;
	public int vrAdrs;
	public int vrLen;
	public int lenAdrs;
	public int lenLen;
	public int lenValue;
	public int length;
	public int valueAdrs;
	public String data;

	RandomAccessFile in;
	DicomXfrSyntax dxs;
	int group2End;
	boolean le = true;

	public DicomElement(
				RandomAccessFile in,
				int adrs, DicomXfrSyntax dxs,
				int group2End) throws Exception {
		this.in = in;
		this.dxs = dxs;
		this.group2End = group2End;

		tagAdrs = adrs;
		tagLen = 4;
		tag = getTag(tagAdrs);
		vrAdrs = tagAdrs + 4;
		le = (adrs < group2End) || ((dxs != null) && dxs.le) || (dxs == null);
		int group = tag & 0xffff0000;
		if ((group != 0xfffe0000) && ((group == 0x20000) || ((dxs != null) && !dxs.ivr))) {
			vr = getVR(vrAdrs);
			if (is4ByteVR(vr)) {
				vrLen = 4;
				lenLen = 4;
			}
			else {
				vrLen = 2;
				lenLen = 2;
			}
		}
		else {
			vr = "  ";
			vrLen = 0;
			lenLen = 4;
		}
		lenAdrs = vrAdrs + vrLen;
		lenValue = getLength(lenAdrs,lenLen);
		valueAdrs = lenAdrs + lenLen;
		length = tagLen + vrLen + lenLen + Math.max(lenValue,0);

		int dataLen = Math.min( Math.max(lenValue, 0), 64);
		data = getPrintableValue(dataLen);
	}

	private int getTag(int adrs) throws Exception {
		int group;
		int element;
		byte[] b = new byte[4];
		in.seek(adrs);
		if (in.read(b) == -1) throw new Exception("EOF");
		if (le) {
			group = ((b[1]<<8) + (b[0] & 0xff)) & 0xffff;
			element = ((b[3]<<8) + (b[2] & 0xff)) & 0xffff;
		}
		else {
			group = ((b[0]<<8) + (b[1] & 0xff)) & 0xffff;
			element = ((b[2]<<8) + (b[3] & 0xff)) & 0xffff;
		}
		return (group<<16) + element;
	}

	private String getVR(int adrs) throws Exception {
		byte[] b = new byte[2];
		in.seek(adrs);
		in.read(b);
		return new String(b);
	}

	private int getLength(int adrs, int len) throws Exception {
		byte[] b = new byte[len];
		in.seek(adrs);
		in.read(b);
		if (len == 2) {
			if (le) return ((b[1]<<8) + (b[0] & 0xff)) & 0xffff;
			else return ((b[0]<<8) + (b[1] & 0xff)) & 0xffff;
		}
		else {
			if (le) return (((b[3]<<8) | (b[2] & 0xff))<<8 | (b[1] & 0xff))<<8 | (b[0] & 0xff);
			else return (((b[0]<<8) | (b[1] & 0xff))<<8 | (b[2] & 0xff))<<8 | (b[3] & 0xff);
		}
	}

	private boolean is4ByteVR(String vr) {
		for (int i=0; i<vrs.length; i++) {
			if (vr.equals(vrs[i])) return true;
		}
		return false;
	}

	public String getStringValue() {
		try {
			in.seek(lenAdrs + lenLen);
			byte[] b = new byte[lenValue];
			in.read(b);
			return new String(b);
		}
		catch (Exception ex) { }
		return "";
	}

	public String getPrintableValue(int len) {
		try {
			if (vr.equals("OB")) {
				int minLen = Math.min(len, 10);
				in.seek(lenAdrs + lenLen);
				byte[] bb = new byte[minLen];
				in.read(bb);
				StringBuffer sb = new StringBuffer();
				for (byte b : bb) sb.append(String.format("\\%02x",b));
				if (len > 10) sb.append(" ...");
				return sb.toString();
			}
			else if (vr.equals("UL") || vr.equals("SL")) {
				if (len != 4) return "";
				int intValue = getIntValue();
				in.seek(lenAdrs + lenLen);
				byte[] bb = new byte[4];
				in.read(bb);
				StringBuffer sb = new StringBuffer();
				for (byte b : bb) sb.append(String.format("\\%02x",b));
				sb.append(String.format(" = %d",intValue));
				return sb.toString();
			}
			else if (vr.equals("US") || vr.equals("SS")) {
				if (len != 2) return "";
				int shortValue = getShortValue();
				in.seek(lenAdrs + lenLen);
				byte[] bb = new byte[2];
				in.read(bb);
				StringBuffer sb = new StringBuffer();
				for (byte b : bb) sb.append(String.format("\\%02x",b));
				sb.append(String.format(" = %d",shortValue));
				return sb.toString();
			}
			else if (vr.equals("FL")) {
				if (len != 4) return "";
				float floatValue = getFloatValue();
				in.seek(lenAdrs + lenLen);
				byte[] bb = new byte[4];
				in.read(bb);
				StringBuffer sb = new StringBuffer();
				for (byte b : bb) sb.append(String.format("\\%02x",b));
				sb.append(String.format(" = %f",floatValue));
				return sb.toString();
			}
			else if (vr.equals("FD")) {
				if (len != 8) return "";
				double doubleValue = getDoubleValue();
				in.seek(lenAdrs + lenLen);
				byte[] bb = new byte[8];
				in.read(bb);
				StringBuffer sb = new StringBuffer();
				for (byte b : bb) sb.append(String.format("\\%02x",b));
				sb.append(String.format(" = %f",doubleValue));
				return sb.toString();
			}
			else if (!vr.equals("SQ") && (tag != 0x7fe00010)) {
				in.seek(lenAdrs + lenLen);
				byte[] bb = new byte[len];
				in.read(bb);
				StringBuffer sb = new StringBuffer();
				for (byte b : bb) sb.append(printable(b));
				return sb.toString();
			}
		}
		catch (Exception ex) { }
		return "";
	}

	private String printable(byte b) {
		byte c[] = new byte[1];
		c[0] = b;
		if ((b >= (byte)0x20) && (b <= (byte)0x7f)) return new String( c );
		return "~";
	}

	public int getIntValue() {
		if (lenValue != 4) return 0;
		try {
			in.seek(lenAdrs + lenLen);
			byte[] b = new byte[lenValue];
			in.read(b);
			if (le) return (((b[3]<<8) | (b[2] & 0xff))<<8 | (b[1] & 0xff))<<8 | (b[0] & 0xff);
			else return (((b[0]<<8) | (b[1] & 0xff))<<8 | (b[2] & 0xff))<<8 | (b[3] & 0xff);
		}
		catch (Exception ex) { }
		return 0;
	}

	public int getShortValue() {
		if (lenValue != 2) return 0;
		try {
			in.seek(lenAdrs + lenLen);
			byte[] b = new byte[lenValue];
			in.read(b);
			if (le) return (b[1] & 0xff)<<8 | (b[0] & 0xff);
			else return (b[0]<<8) | (b[1] & 0xff);
		}
		catch (Exception ex) { }
		return 0;
	}
	
	public float getFloatValue() {
		if (lenValue != 4) return 0;
		try {
			in.seek(lenAdrs + lenLen);
			byte[] b = new byte[lenValue];
			in.read(b);
			if (le) {
				ByteBuffer bb = ByteBuffer.wrap(b);
				bb.order(ByteOrder.LITTLE_ENDIAN);
				return bb.getFloat();
			}
			else {
				ByteBuffer bb = ByteBuffer.wrap(b);
				bb.order(ByteOrder.BIG_ENDIAN);
				return bb.getFloat();
			}
		}
		catch (Exception ex) { }
		return 0;
	}
	
	public double getDoubleValue() {
		if (lenValue != 8) return 0;
		try {
			in.seek(lenAdrs + lenLen);
			byte[] b = new byte[lenValue];
			in.read(b);
			if (le) {
				ByteBuffer bb = ByteBuffer.wrap(b);
				bb.order(ByteOrder.LITTLE_ENDIAN);
				return bb.getDouble();
			}
			else {
				ByteBuffer bb = ByteBuffer.wrap(b);
				bb.order(ByteOrder.BIG_ENDIAN);
				return bb.getDouble();
			}
		}
		catch (Exception ex) { }
		return 0;
	}
	
	public boolean isItemTag() {
		return (tag == 0xFFFEE000);
	}
	
	public boolean isSequenceDelimiterTag() {
		return (tag == 0xFFFEE0DD);
	}
	
	public int[] getIntArray() {
		try {
			if (lenValue%4 == 0) {
				int[] ints = new int[lenValue/4];
				in.seek(lenAdrs + lenLen);
				byte[] b = new byte[lenValue];
				in.read(b);
				for (int i=0; i<lenValue/4; i++) {
					int base = 4*i;
					if (le) ints[i] = (((b[base+3]<<8) | (b[base+2] & 0xff))<<8 | (b[base+1] & 0xff))<<8 | (b[base+0] & 0xff);
					else ints[i] = (((b[base+0]<<8) | (b[base+1] & 0xff))<<8 | (b[base+2] & 0xff))<<8 | (b[base+3] & 0xff);
				}
				return ints;
			}
		}
		catch (Exception ex) { }
		return new int[0];
	}

	public boolean isSQ() {
		return vr.equals("SQ");
	}

	public boolean isPrivateGroup() {
		return ((tag & 0x10000) != 0);
	}

	public boolean isPixels() {
		return (tag == 0x7fe00010);
	}

	public boolean isGroupLength() {
		return ((tag & 0xffff) == 0);
	}

	public boolean isMetadata() {
		return ((tag >>> 16) < 8);
	}

	static String zeroes = "00000000";
	static String spaces = "         ";
	public String toString() {
		return toString(true);
	}
	public String toString(boolean showItemValue) {
		String adrsString = Integer.toHexString(tagAdrs);
		adrsString = spaces.substring(0,8 - adrsString.length()) + adrsString;
		String tagString = Integer.toHexString(tag);
		tagString = zeroes.substring(0,8 - tagString.length()) + tagString;
		tagString = "(" + tagString.substring(0,4) + "," + tagString.substring(4) + ")";
		String lenString = Integer.toHexString(lenValue);
		lenString = spaces.substring(0,9 - lenString.length()) + lenString;
		return ((adrsString + "/ " + tagString + " " + vr + lenString).toUpperCase() + " " + (showItemValue?data:"") + "\n");
	}
}

