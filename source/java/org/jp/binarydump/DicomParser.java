package org.jp.binarydump;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.text.*;

public class DicomParser extends Parser implements MouseListener {

	int offset;
	ColorField[] fields;
	int[] addresses;
	DicomElement pixels = null;
	DicomXfrSyntax dxs = null;
	DicomSopClass dsc = null;
	int group2End = Integer.MAX_VALUE;
	int lastIndex = 0;
	LinkedList<DicomElement> elementList;

	AttachedFrame listFrame = null;
	ScrolledEditorPanel editorPanel = null;
	JEditorPane editor = null;
	AttachedFrame botFrame = null;
	ScrolledEditorPanel botPanel = null;
	JEditorPane botEditor = null;

	static byte[] seqDelimTag = { (byte)0xFE, (byte)0xFF, (byte)0xDD, (byte)0xE0, 0, 0, 0, 0 };

	public DicomParser(BinaryDump parent, RandomAccessFile in) {
		super(parent, in);
		getParams();
		if (type) parse();
	}

	public String getContentType() {
		if (dxs == null) return "Content-Type: DICOM [No TS, IVRLE assumed]";
		else return "Content-Type: DICOM " + dxs.name;
	}

	public Color getColorFor(int adrs) {
		if (lastIndex < addresses.length - 1) {
			if ((adrs >= addresses[lastIndex]) &&
				(adrs < addresses[lastIndex+1])) {
				return fields[lastIndex].color;
			}
			else if (adrs == addresses[lastIndex+1]) {
				lastIndex++;
				return fields[lastIndex].color;
			}
		}
		int index = Arrays.binarySearch(addresses,adrs);
		if (index < addresses.length) {
			if (index >= 0) lastIndex = index;
			else lastIndex = -index - 2;
			return fields[lastIndex].color;
		}
		return Color.black;
	}

	public JMenu getMenu() {
		JMenu menu = new JMenu("DICOM");
		JMenuItem listItem = new JMenuItem("List Elements");
		listItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				listElements();
			}
		});
		listItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_MASK));
		menu.add(listItem);
		if (pixels != null) {
			JMenuItem saveItem = new JMenuItem("Save Pixels");
			saveItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					savePixels();
				}
			});
			saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_MASK));
			menu.add(saveItem);

			JMenuItem listbotItem = new JMenuItem("Check BasicOffsetTable");
			listbotItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					listBasicOffsetTable();
				}
			});
			listbotItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_MASK));
			menu.add(listbotItem);

			JMenuItem truncateItem = new JMenuItem("Truncate after Pixels");
			truncateItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					truncate();
				}
			});
			truncateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_MASK));
			menu.add(truncateItem);

			JMenuItem removePrivateGroupsItem = new JMenuItem("Remove Private Groups");
			removePrivateGroupsItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					removePrivateGroups();
				}
			});
			removePrivateGroupsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_MASK));
			menu.add(removePrivateGroupsItem);

			JMenuItem fixGroupLengthElementsItem = new JMenuItem("Fix Group Length Elements");
			fixGroupLengthElementsItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					fixGroupLengthElements();
				}
			});
			menu.add(fixGroupLengthElementsItem);
		}
		return menu;
	}

	private void listElements() {
		listFrame = new AttachedFrame(parent, parent.getFile().getName(), 750, Color.white);
		editorPanel = new ScrolledEditorPanel();
		listFrame.setCenterComponent(editorPanel);
		ListIterator<DicomElement> it = elementList.listIterator(0);
		StringBuffer sb = new StringBuffer();
		if (dsc != null) sb.append(dsc.name + "\n\n");
		boolean showItemData = false;
		while (it.hasNext()) {
			DicomElement el = it.next();
			showItemData |= el.isPixels();
			sb.append(el.toString(!el.isItemTag() || showItemData));
		}
		editor = editorPanel.getEditor();
		editor.setText(sb.toString());
		editor.addMouseListener(this);
		listFrame.setVisible(true);
		listFrame.attach();
	}
	
	private void listBasicOffsetTable() {
		ListIterator<DicomElement> it = elementList.listIterator(0);
		while (it.hasNext()) {
			DicomElement de = it.next();
			if (de.isPixels()) {
				if (de.lenValue == -1) {
					if (it.hasNext()) {
						DicomElement bot = it.next();
						if (bot.isItemTag()) {
							int baseAdrs = -1;
							int[] botInts = bot.getIntArray();
							StringBuffer sb = new StringBuffer("Basic Offset Table\n");
							sb.append("Frame      Offset      Address      ItemTag\n");
							sb.append("-----      ------      -------      -------\n");
							for (int k=0; k<botInts.length; k++) {
								if (it.hasNext()) {
									DicomElement e = it.next();
									if (baseAdrs == -1) baseAdrs = e.tagAdrs;
									int adrs = baseAdrs + botInts[k];
									int tag = e.tagAdrs;
									String ok = (adrs == tag) ? "     " : "***  ";
									sb.append(String.format("%4d %12x %12x %12x  %s\n", (k+1), botInts[k], adrs, tag, ok));
								}
							}
							botFrame = new AttachedFrame(parent, parent.getFile().getName(), 450, Color.white);
							botPanel = new ScrolledEditorPanel();
							botFrame.setCenterComponent(botPanel);
							botEditor = botPanel.getEditor();
							botEditor.setText(sb.toString());
							botFrame.setVisible(true);
							botFrame.attach();
						}
					}
				}
				break;
			}
		}
	}

	public void mouseClicked(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { }
	public void mousePressed(MouseEvent e) { }

	static Pattern pattern = Pattern.compile("\\s*([0-9a-f-A-F]{1,8})/\\s*(\\([0-9a-f-A-F]{1,4},[0-9a-f-A-F]{1,4}\\)).*");
	public void mouseReleased(MouseEvent e) {
		if (editor != null) {
			int dot = editor.getCaretPosition();
			if (dot < 20) return;
			try {
				int rowStart = Utilities.getRowStart(editor, dot);
				String text = editor.getText(rowStart, 60);
				Matcher matcher = pattern.matcher(text);
				if (matcher.find()) {
					String adrsString = matcher.group(1);
					String tagString = matcher.group(2);
					String tag = DicomDictionary.getInstance().get(tagString);
					listFrame.setMessage(tagString + ": " + tag);
					//System.out.println(adrsString+"  "+tagString);
					int adrs = Integer.parseInt(adrsString, 16);
					parent.gotoAddress(adrs);
				}
			}
			catch (Exception ignore) { ignore.printStackTrace(); }
		}
	}
	
	private void savePixels() {
		File outputFile = null;
		if (pixels != null) {
			JFileChooser chooser = new JFileChooser(parent.dataFile.getParentFile());
			chooser.setSelectedFile(new File(parent.dataFile.getAbsolutePath() + ".pixels"));
			if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
				outputFile = chooser.getSelectedFile();
				BufferedOutputStream bos = null;
				try {
					bos = new BufferedOutputStream(new FileOutputStream(outputFile));
					if (pixels.lenValue > 0) write(bos, pixels.valueAdrs, pixels.lenValue);
					else if (pixels.lenValue == 0xffffffff) {
						boolean firstItem = true;
						DicomElement el;
						int adrs = pixels.tagAdrs + pixels.length;
						while ((el = nextElement(adrs)) != null) {
							if (el.tag == 0xfffee000) {
								if (!firstItem) write(bos, el.valueAdrs, el.lenValue);
								adrs += el.length;
								firstItem = false;
							}
							else break;
						}
					}
				}
				catch (Exception ex) { }
				finally {
					if (bos != null) {
						try { bos.flush(); }
						catch (Exception ignore) { }
						try { bos.close(); }
						catch (Exception ignore) { }
					}
				}
			}
		}
	}

	private void truncate() {
		DicomElement stopElement = nextElementAfter(pixels);
		if (stopElement != null) {
			JFileChooser chooser = new JFileChooser(parent.dataFile.getParentFile());
			chooser.setSelectedFile(new File(parent.dataFile.getAbsolutePath() + ".truncated.dcm"));
			if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
				File outputFile = chooser.getSelectedFile();
				BufferedOutputStream bos = null;
				try {
					bos = new BufferedOutputStream(new FileOutputStream(outputFile));
					write(bos, 0, stopElement.tagAdrs);
				}
				catch (Exception ex) { }
				finally {
					if (bos != null) {
						try { bos.flush(); }
						catch (Exception ignore) { }
						try { bos.close(); }
						catch (Exception ignore) { }
					}
				}
			}
		}
	}

	private void removePrivateGroups() {
		ListIterator<DicomElement> lit = elementList.listIterator();
		if (!lit.hasNext()) return;
		DicomElement currentElement = lit.next();

		JFileChooser chooser = new JFileChooser(parent.dataFile.getParentFile());
		chooser.setSelectedFile(new File(parent.dataFile.getAbsolutePath() + ".rpg.dcm"));
		if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
			File outputFile = chooser.getSelectedFile();
			BufferedOutputStream bos = null;
			try {
				bos = new BufferedOutputStream(new FileOutputStream(outputFile));
				write(bos, 0, currentElement.tagAdrs);
				DicomElement nextElement = null;
				while (!currentElement.isPixels() && lit.hasNext()) {
					nextElement = lit.next();
					if (!currentElement.isPrivateGroup()) {
						write(bos, currentElement.tagAdrs, nextElement.tagAdrs - currentElement.tagAdrs);
					}
					if (currentElement.isPixels()) break;
					currentElement = nextElement;
					nextElement = null;
				}
				if (currentElement.isPixels()) {
					int len = (int)(in.length() - currentElement.tagAdrs);
					nextElement = nextElementAfter(pixels);
					if (nextElement != null) len = nextElement.tagAdrs - currentElement.tagAdrs;
					write(bos, currentElement.tagAdrs, len);
				}
			}
			catch (Exception ex) { }
			finally {
				if (bos != null) {
					try { bos.flush(); }
					catch (Exception ignore) { }
					try { bos.close(); }
					catch (Exception ignore) { }
				}
			}
		}
	}

	private void fixGroupLengthElements() {
		ListIterator<DicomElement> lit = elementList.listIterator();
		if (!lit.hasNext()) return;
		DicomElement currentElement = lit.next();

		JFileChooser chooser = new JFileChooser(parent.dataFile.getParentFile());
		chooser.setSelectedFile(new File(parent.dataFile.getAbsolutePath() + ".fgle.dcm"));
		if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
			File outputFile = chooser.getSelectedFile();
			BufferedOutputStream bos = null;
			try {
				bos = new BufferedOutputStream(new FileOutputStream(outputFile));
				write(bos, 0, currentElement.tagAdrs);
				DicomElement nextElement = null;
				while (!currentElement.isPixels() && lit.hasNext()) {
					nextElement = lit.next();
					if (!currentElement.isGroupLength()) {
						write(bos, currentElement.tagAdrs, nextElement.tagAdrs - currentElement.tagAdrs);
					}
					else {
						int lengthToEnd = 0;
						DicomElement nextGroup = nextGroupAfter(currentElement);
						//System.out.println(Integer.toHexString(currentElement.tag)+
						//				": nextGroup: "+((nextGroup==null) ? "null" : Integer.toHexString(nextGroup.tag)));
						if (nextGroup != null) {
							lengthToEnd = nextGroup.tagAdrs - currentElement.tagAdrs - currentElement.length;
						}
						else {
							lengthToEnd = (int)parent.getFile().length() - currentElement.tagAdrs - currentElement.length;
							//System.out.println("...lengthToEnd = "+Integer.toHexString(lengthToEnd));
						}
						write(bos, currentElement, lengthToEnd);
					}
					if (currentElement.isPixels()) break;
					currentElement = nextElement;
					nextElement = null;
				}
				if (currentElement.isPixels()) {
					int len = (int)(in.length() - currentElement.tagAdrs);
					nextElement = nextElementAfter(pixels);
					if (nextElement != null) len = nextElement.tagAdrs - currentElement.tagAdrs;
					write(bos, currentElement.tagAdrs, len);
				}
			}
			catch (Exception ex) { }
			finally {
				if (bos != null) {
					try { bos.flush(); }
					catch (Exception ignore) { }
					try { bos.close(); }
					catch (Exception ignore) { }
				}
			}
		}
	}

	private void write(BufferedOutputStream bos, int adrs, int length) throws Exception {
		int n;
		in.seek(adrs);
		byte[] b = new byte[Math.min(length, 4096)];
		while ((length > 0) && ((n=in.read(b, 0, Math.min(b.length, length))) > 0)) {
			bos.write(b, 0, n);
			length -= n;
		}
	}

	private void write(BufferedOutputStream bos, DicomElement el, int value) throws Exception {
		write(bos, el.tagAdrs, el.valueAdrs - el.tagAdrs);
		byte[] b = new byte[4];
		b[0] = (byte)(value & 0xff);
		b[1] = (byte)((value >>> 8) & 0xff);
		b[2] = (byte)((value >>> 16) & 0xff);
		b[3] = (byte)((value >>> 24) & 0xff);
		bos.write(b, 0, 4);
	}

	private void getParams() {
		type = true;
		if (checkForDICM(0x80)) offset = 0x80 + 4;
		else if (checkForDICM(0)) offset = 4;
		else if (checkBeginning()) offset = 0;
		else type = false;
	}

	private boolean checkForDICM(int adrs) {
		try {
			byte[] b = new byte[4];
			in.seek(adrs);
			in.read(b);
			String dicm = new String(b);
			return dicm.equals("DICM");
		}
		catch (Exception ex) { return false; }
	}

	private boolean checkBeginning() {
		try {
			byte[] b = new byte[2];
			byte[] sig2 = new byte[] { 0x02, 0x00 };
			byte[] sig8 = new byte[] { 0x08, 0x00 };
			in.seek(0);
			in.read(b);
			return ((b[0] == sig2[0]) && (b[1] == sig2[1])) ||
				   ((b[0] == sig8[0]) && (b[1] == sig8[1]));
		}
		catch (Exception ex) { return false; }
	}

	private void parse() {
		elementList = new LinkedList<DicomElement>();
		LinkedList<ColorField> list = new LinkedList<ColorField>();
		//Set up for the preamble and the identifier
		if (offset > 4) list.add(new ColorField(Color.black,0));
		if (offset > 0) list.add(new ColorField(Color.gray,offset-4));
		//Now do all the elements
		int adrs = offset;
		DicomElement el;
		int count = 0;
		while ((el = nextElement(adrs)) != null) {
			elementList.add(el);
			if (el.tag == 0x00020000) group2End = el.tagAdrs + el.length + el.getIntValue();
			if (el.tag == 0x00020010) dxs = DicomXfrSyntax.getSyntax(el.getStringValue());
			if ((el.tag >= 0x00030000) && (group2End == Integer.MAX_VALUE)) group2End = el.tagAdrs;
			if (el.tag == 0x00080016) dsc = DicomSopClass.getSopClass(el.getStringValue());
			if (el.tag == 0x7fe00010) pixels = el;
			list.add(new ColorField(Color.red,el.tagAdrs));
			if (el.vrLen > 0) list.add(new ColorField(Color.orange,el.vrAdrs));
			list.add(new ColorField(Color.blue,el.lenAdrs));
			if (el.lenValue > 0) list.add(new ColorField(Color.black,el.lenAdrs+el.lenLen));
			if (el.isSQ() || (el.isItemTag() && (pixels==null))) adrs = el.valueAdrs;
			else adrs += el.length;
		}
		int len = list.size();
		fields = new ColorField[len];
		addresses = new int[len];
		ListIterator<ColorField> it = list.listIterator(0);
		for (int i=0; i<len; i++) {
			fields[i] = it.next();
			addresses[i] = fields[i].address;
		}
	}

	private DicomElement nextElement(int adrs) {
		DicomElement el;
		try { el = new DicomElement(in, adrs, dxs, group2End); }
		catch (Exception ex) { return null; }
		if (el.length <= 0) return null;
		return el;
	}

	private DicomElement nextElementAfter(DicomElement target) {
		DicomElement next = null;
		ListIterator<DicomElement> lit = elementList.listIterator();
		while (lit.hasNext()) {
			DicomElement current = lit.next();
			if (current.tag == target.tag) {
				while (lit.hasNext()) {
					next = lit.next();
					if ((next.tag & 0xffff0000) != 0xfffe0000) break;
					next = null;
				}
			}
		}
		return next;
	}

	private DicomElement nextGroupAfter(DicomElement target) {
		DicomElement next = null;
		ListIterator<DicomElement> lit = elementList.listIterator();
		while (lit.hasNext()) {
			DicomElement current = lit.next();
			if (current.tag == target.tag) {
				while (lit.hasNext()) {
					next = lit.next();
					if ((next.tag & 0xffff0000) != (current.tag & 0xffff0000)) break;
					next = null;
				}
			}
		}
		return next;
	}

	private boolean isSequenceDelimitationTag(byte[] sdt) {
		if (sdt.length < seqDelimTag.length) return false;
		for (int i=0; i<seqDelimTag.length; i++) {
			if (sdt[i] != seqDelimTag[i]) return false;
		}
		return true;
	}

	class ColorField {
		Color color;
		int address;
		public ColorField(Color color, int address) {
			this.color = color;
			this.address = address;
		}
	}

}
