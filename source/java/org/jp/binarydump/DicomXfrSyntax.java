package org.jp.binarydump;

public class DicomXfrSyntax {

	static DicomXfrSyntax[] syntaxes = new DicomXfrSyntax[] {
		new DicomXfrSyntax(
			"1.2.840.10008.1.2",
			"Implicit VR Little Endian",
			true,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.1",
			"Explicit VR Little Endian",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.1.99",
			"Deflated Explicit VR Little Endian",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.2",
			"Explicit VR Big Endian",
			false,false),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.50",
			"JPEG Baseline (Process 1)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.51",
			"JPEG Extended (Process 2 & 4)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.52",
			"JPEG Extended (Process 3 & 5)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.53",
			"JPEG Spectral Selection, Non-Hierarchical (Process 6 & 8)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.54",
			"JPEG Spectral Selection, Non-Hierarchical (Process 7 & 9)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.55",
			"JPEG Full Progression, Non-Hierarchical (Process 10 & 12)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.56",
			"JPEG Full Progression, Non-Hierarchical (Process 11 & 13)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.57",
			"JPEG Lossless, Non-Hierarchical (Process 14)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.58",
			"JPEG Lossless, Non-Hierarchical (Process 15)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.59",
			"JPEG Extended, Hierarchical (Process 16 & 18)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.60",
			"JPEG Extended, Hierarchical (Process 17 & 19)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.61",
			"JPEG Spectral Selection, Hierarchical (Process 20 & 22)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.62",
			"JPEG Spectral Selection, Hierarchical (Process 21 & 23)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.63",
			"JPEG Full Progression, Hierarchical (Process 24 & 26)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.64",
			"JPEG Full Progression, Hierarchical (Process 25 & 27)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.65",
			"JPEG Lossless, Hierarchical (Process 28)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.66",
			"JPEG Lossless, Hierarchical (Process 29)",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.70",
			"JPEG Lossless, Non-Hierarchical, First-Order Prediction (Process 14 [Selection Value 1])",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.80",
			"JPEG-LS Lossless Image Compression",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.81",
			"JPEG-LS Lossy (Near-Lossless) Image Compression",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.90",
			"JPEG 2000 Lossless Image Compression",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.91",
			"JPEG 2000 Lossy Image Compression",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.4.100",
			"MPEG2 Main Profile @ Main Level",
			false,true),
		new DicomXfrSyntax(
			"1.2.840.10008.1.2.5",
			"RLE Lossless",
			false,true)
	};

	public String uid;
	public String name;
	public boolean ivr;
	public boolean le;

	public DicomXfrSyntax(String uid, String name, boolean ivr, boolean le) {
		this.uid = uid;
		this.name = name;
		this.ivr = ivr;
		this.le = le;
	}

	public static DicomXfrSyntax getSyntax(String uid) {
		uid = uid.trim();
		for (int i=0; i<syntaxes.length; i++) {
			if (syntaxes[i].uid.equals(uid)) return syntaxes[i];
		}
		return null;
	}
}

