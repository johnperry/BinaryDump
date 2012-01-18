package org.jp.binarydump;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;

public class BinaryDump extends JFrame {

	static Color 	background = Color.getHSBColor(0.58f, 0.17f, 0.95f);
	JFileChooser 	chooser = null;
    File			dataFile = null;
    TextPanel		textPanel;
    FooterPanel		footerPanel;
    String			windowTitle = "Binary Dump Utility - v2";
    int 			width = 580;
    int 			height = 700;
    JMenu 			parserMenu = null;

    public static void main(String[] args) {
        new BinaryDump();
    }

    public BinaryDump() {
    	initComponents();
    	openFile();
    }

	private void openFile() {
		if (chooser == null) {
			File here = new File(System.getProperty("user.dir"));
			chooser = new JFileChooser(here);
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			dataFile = chooser.getSelectedFile();
			setTitle(dataFile.getAbsolutePath());
			textPanel.setFile(dataFile);
			JMenuBar jmb = getJMenuBar();
			if (parserMenu != null) jmb.remove(parserMenu);
			parserMenu = textPanel.parser.getMenu();
			if (parserMenu != null) jmb.add(parserMenu);
			setJMenuBar(jmb);
		}
	}

	private void fileProperties() {
		if (dataFile != null) {
			JOptionPane.showMessageDialog(this,textPanel.fileProperties);
		}
	}

	public void redisplay() {
		if (textPanel != null) textPanel.dumpFile();
	}

	public File getFile() {
		return dataFile;
	}

    private void initComponents() {
		setTitle(windowTitle);
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu();
		fileMenu.setText("File");

		JMenuItem open = new JMenuItem("Open file...");
		open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,InputEvent.CTRL_MASK));
		open.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				openFile();
			}
		});
		fileMenu.add(open);

		JMenuItem props = new JMenuItem("File properties...");
		props.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				fileProperties();
			}
		});
		fileMenu.add(props);

		JMenuItem exit = new JMenuItem("Exit");
		exit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				exitApp();
			}
		});
		fileMenu.add(exit);

		menuBar.add(fileMenu);
		setJMenuBar(menuBar);

		JPanel main = new JPanel();
		main.setLayout(new BorderLayout());
		textPanel = new TextPanel(this);
		footerPanel = new FooterPanel();
		main.add(textPanel,BorderLayout.CENTER);
		main.add(footerPanel,BorderLayout.SOUTH);
		getContentPane().add(main, BorderLayout.CENTER);
		pack();
		centerFrame();
		this.setVisible(true);
		addWindowListener(
			new WindowAdapter() {
				public void windowClosing(WindowEvent evt) {
					exitApp();
				}
			}
		);
		addComponentListener(
			new ComponentAdapter() {
				public void componentResized(ComponentEvent evt) {
					resize(evt);
				}
			}
		);
    }

    private void centerFrame() {
		Toolkit t = getToolkit();
		Dimension scr = t.getScreenSize ();
		setSize(width,height);
		setLocation(
			new Point(
				(scr.width - width)/2,
				(scr.height - height)/2));
    }

    private void exitApp() {
		System.exit(0);
    }

    private void resize(ComponentEvent evt) {
		Dimension size = getSize();
		size.width = width;
		setSize(size);
		textPanel.dumpFile();
	}

	class FooterPanel extends JPanel {
		public JLabel message;
		public FooterPanel() {
			super();
			this.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			this.setLayout(new FlowLayout(FlowLayout.LEADING));
			this.setBackground(background);
			message = new JLabel(".");
			this.add(message);
		}
		public void setMessage(String msg) {
			message.setText(msg);
		}
	}

	class TextPanel extends JPanel implements AdjustmentListener, MouseWheelListener {
		BinaryDump parent;
		int rowcount = 20;
		int bytesperrow = 16;
	    JScrollBar scrollbar;
		int adrs;
		int fileLength;
		RandomAccessFile in = null;
		ColorPane text;
		public Parser parser;
		public String fileProperties;

		public TextPanel(BinaryDump parent) {
			super();
			this.parent = parent;
			setLayout(new BorderLayout());
        	text = new ColorPane();
	        scrollbar = new JScrollBar ();
			scrollbar.setUnitIncrement (16);
			scrollbar.setBlockIncrement (256);
			scrollbar.addAdjustmentListener(this);
			text.addMouseWheelListener(this);
        	add(scrollbar, BorderLayout.EAST);
        	add(text, BorderLayout.CENTER);
		}

		void setFile(File file) {
			try {
				if (in != null) in.close();
                in = new RandomAccessFile(file, "rws");
                parser = Parser.getInstance(parent,in);
                adrs = 0;
                scrollbar.setValue (0);
                scrollbar.setMinimum (0);
                long longFileLength = in.length();
                if (longFileLength < Integer.MAX_VALUE) fileLength = (int)longFileLength;
                else fileLength = Integer.MAX_VALUE;
				scrollbar.setMaximum (fileLength-1);
                dumpFile();
                String contentType = parser.getContentType();
				footerPanel.setMessage(contentType + "; length = "+longFileLength);
				fileProperties =
					dataFile.getAbsolutePath() + "\n" +
					"Length = " + fileLength + "\n" +
					contentType + "\n";
				}
            catch (Exception ex) {
				footerPanel.setMessage("Exception: "+ex.getMessage());
            }
		}

		public void adjustmentValueChanged(AdjustmentEvent evt) {
			int i = scrollbar.getValue() & 0xfffffff0;
			if (i != adrs) {
				adrs = i;
				dumpFile();
			}
		}

		public void mouseWheelMoved(MouseWheelEvent evt) {
			int deltaAdrs = evt.getUnitsToScroll() * 2 * 0x10;
			scrollbar.setValue(scrollbar.getValue() + deltaAdrs);
		}

		public void dumpFile () {
			if (in == null) return;
			text.setText("");
			rowcount = text.getHeight() / text.lineHeight;
			byte b[] = new byte[bytesperrow];
			byte c[] = new byte[1];
			int i,j;
			int n = 0;
			boolean done = false;
			int nrows = rowcount;
			if (adrs < 0) adrs = 0;
			if (adrs >= fileLength) adrs = fileLength - 1;
			adrs = adrs & 0xfffffff0;
			scrollbar.setValue(adrs);

			try { in.seek(adrs); }
			catch (IOException ex) {
				footerPanel.setMessage("Error seeking address "+Integer.toHexString(adrs));
				return;
			}

			int iadrs = adrs;
			Color[] colors = new Color[bytesperrow];

			while (!done && (nrows-- > 0)) {
				try {
					n = in.read(b);
					if (n == -1) {
						done = true;
						break;
					}
				}
				catch (IOException ex) {
					footerPanel.setMessage("I/O Exception: "+ex);
					done = true;
				}
				text.append(Color.gray,addressString(iadrs)+"/ ");
				for (j=0; j<bytesperrow; j++) {
					if (j<n) {
						colors[j] = parser.getColorFor(iadrs+j);
						text.append(colors[j],byteString(b[j]) + " ");
					}
					else text.append("   ");
				}
				text.append(Color.gray,"|");
				for (j=0; j<bytesperrow; j++) {
					if (j<n) text.append(colors[j],printable(b[j]));
					else text.append(" ");
				}
				text.append(Color.gray,"|\n");
				iadrs += bytesperrow;
			}
		}

		private String addressString(int adrs) {
			int i;
			int a = adrs;
			String s = "";
			for (i=0; i<8; i++)
			{
				s = makehex(a) + s;
				a =  a >> 4;
			}
			return s;
		}

		private String byteString(byte b) {
			int n = (int)b;
			return makehex( (n>>4) ) + makehex (n);
		}

		private String makehex(int n) {
			int nn = n & 0xf;
			byte b[] = new byte[1];
			b[0] = (byte) nn;
			if (nn < 10)b[0] += '0';
			else b[0] += 'A' - (byte) 10;
			return new String(b);
		}

		private String printable(byte b) {
			byte c[] = new byte[1];
			c[0] = b;
			if ((b >= (byte)0x20) && (b <= (byte)0x7f)) return new String( c );
			return ".";
		}
	}

}
