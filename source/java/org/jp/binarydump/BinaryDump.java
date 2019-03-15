package org.jp.binarydump;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;

public class BinaryDump extends JFrame {

	public static Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);
	JFileChooser 	chooser = null;
    File			dataFile = null;
    TextPanel		textPanel;
    FooterPanel		footerPanel;
    String			windowTitle = "Binary Dump Utility - v2";
    int 			width = 580;
    int 			height = 700;
    JMenu 			parserMenu = null;
    PropertiesFile  props = null;
    LinkedList<String> recent = null;
    JMenu			recentMenu = null;
    WindowCloser	closer;

    public static void main(String[] args) {
        new BinaryDump(args);
    }

    public BinaryDump(String[] args) {
		props = new PropertiesFile(new File("BinaryDump.properties"));
		recent = getRecentFiles();
		closer = new WindowCloser(this);
		addWindowListener(closer);
    	initComponents();
    	if (args.length > 0) {
			final String name = args[0];
			Runnable r = new Runnable() {
				public void run() {
					openFile(name);
				}
			};
			SwingUtilities.invokeLater(r);
		}
    	else openFile();
    }

	private void openFile(String name) {
		if (name != null){
			name = name.trim();
			if (!name.equals("")) {
				File file = new File(name);
				if (file.exists()) {
					openFile(file);
					return;
				}
			}
		}
		openFile();
	}
	
	private void openFile() {
		if (chooser == null) {
			File here = getMostRecentFile();
			if ((here == null) || !here.exists()) {
				here = new File(System.getProperty("user.dir"));
				String dir = props.getProperty("dir");
				if (dir != null) {
					File dirFile = new File(dir);
					if (dirFile.exists()) here = dirFile;
				}
			}
			chooser = new JFileChooser(here);
			if (here.isFile()) chooser.setSelectedFile(here);
			chooser.getActionMap().get("viewTypeDetails").actionPerformed(null);
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			dataFile = chooser.getSelectedFile().getAbsoluteFile();
			openFile(dataFile);
		}
	}
	
	private void openFile(File dataFile) {
   		footerPanel.setMessage(dataFile.getAbsolutePath());
		this.dataFile = dataFile;
		props.setProperty("dir", dataFile.getParentFile().getAbsolutePath());
		setTitle(dataFile.getAbsolutePath());
		textPanel.setFile(dataFile);
		JMenuBar jmb = getJMenuBar();
		if (parserMenu != null) jmb.remove(parserMenu);
		parserMenu = textPanel.parser.getMenu();
		if (parserMenu != null) jmb.add(parserMenu);
		setJMenuBar(jmb);
		updateRecentFiles(dataFile);
	}

	private void updateRecentFiles(File dataFile) {
		addRecentFile(dataFile);
		saveRecentFiles();
		props.store();
		setRecentMenuItems();
	}

	private LinkedList<String> getRecentFiles() {
		String[] keys = new String[0];
		keys = props.stringPropertyNames().toArray(keys);
		Arrays.sort(keys);
		LinkedList<String> recent = new LinkedList<String>();
		for (String key : keys) {
			if (key.startsWith("recent[")) recent.add(props.getProperty(key));
		}
		return recent;
	}
	
	private File getMostRecentFile() {
		LinkedList<String> recent = getRecentFiles();
		if (recent.size() > 0) return new File(recent.peekFirst());
		return null;
	}

	private void addRecentFile(File file) {
		String path = file.getAbsolutePath();
		int k = -1;
		while ( (k=recent.indexOf(path)) != -1 ) recent.remove(k);
		recent.push(path);
		if (recent.size() > 10) recent.removeLast();
	}

	private void saveRecentFiles() {
		for (String name : props.stringPropertyNames()) {
			if (name.startsWith("recent[")) {
				props.remove(name);
			}
		}
		int k = 0;
		for (String file : recent) {
			props.setProperty("recent["+k+"]", file);
			k++;
		}
	}

	private void setRecentMenuItems() {
		recentMenu.removeAll();
		for (String file : recent) {
			JMenuItem recentItem = new JMenuItem(file);
			recentItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					JMenuItem fileItem = (JMenuItem)evt.getSource();
					openFile( new File(fileItem.getText()) );
				}
			});
			recentMenu.add(recentItem);
		}
	}

	private void gotoAddress() {
		if (dataFile != null) {
			String s = JOptionPane.showInputDialog(this, "Go to address:");
			try {
				int adrs = Integer.parseInt(s.trim(), 16);
				textPanel.dumpFile(adrs);
			}
			catch (Exception ignore) { }
		}
	}

	public void gotoAddress(int adrs) {
		textPanel.dumpFile(adrs);
	}

	private void fileProperties() {
		if (dataFile != null) {
			JOptionPane.showMessageDialog(this, textPanel.fileProperties);
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

		JMenuItem openItem = new JMenuItem("Open file...");
		openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,InputEvent.CTRL_MASK));
		openItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				openFile();
			}
		});
		fileMenu.add(openItem);

		recentMenu = new JMenu();
		recentMenu.setText("Open recent file");
		fileMenu.add(recentMenu);
		setRecentMenuItems();

		JMenuItem gotoItem = new JMenuItem("Go to...");
		gotoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G,InputEvent.CTRL_MASK));
		gotoItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				gotoAddress();
			}
		});
		fileMenu.add(gotoItem);

		JMenuItem propsItem = new JMenuItem("File properties...");
		propsItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				fileProperties();
			}
		});
		fileMenu.add(propsItem);

		JMenuItem exitItem = new JMenuItem("Exit");
		exitItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				closer.close();
			}
		});
		fileMenu.add(exitItem);

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
		positionFrame();
		this.setVisible(true);
		addComponentListener(
			new ComponentAdapter() {
				public void componentResized(ComponentEvent evt) {
					resize(evt);
				}
			}
		);
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

		public void dumpFile(int adrs) {
			scrollbar.setValue(adrs & 0xfffffff0);
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

    class WindowCloser extends WindowAdapter {
		JFrame parent;
		public WindowCloser(JFrame parent) {
			this.parent = parent;
		}
		public void windowClosing(WindowEvent evt) {
			close();
		}
		public void close() {
			Point p = getLocation();
			props.put("x", Integer.toString(p.x));
			props.put("y", Integer.toString(p.y));
			Toolkit t = getToolkit();
			Dimension d = parent.getSize ();
			props.put("w", Integer.toString(d.width));
			props.put("h", Integer.toString(d.height));
			props.store();
			System.exit(0);
		}
    }

	private void positionFrame() {
		int x = getInt( props.getProperty("x"), 0 );
		int y = getInt( props.getProperty("y"), 0 );
		int w = getInt( props.getProperty("w"), 0 );
		int h = getInt( props.getProperty("h"), 0 );
		boolean noProps = ((w == 0) || (h == 0));
		int wmin = 550;
		int hmin = 600;
		if ((w < wmin) || (h < hmin)) {
			w = wmin;
			h = hmin;
		}
		if ( noProps || !screensCanShow(x, y) || !screensCanShow(x+w-1, y+h-1) ) {
			Toolkit t = getToolkit();
			Dimension scr = t.getScreenSize ();
			x = (scr.width - wmin)/2;
			y = (scr.height - hmin)/2;
			w = wmin;
			h = hmin;
		}
		setSize( w, h );
		setLocation( x, y );
	}

	private boolean screensCanShow(int x, int y) {
		GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screens = env.getScreenDevices();
		for (GraphicsDevice screen : screens) {
			GraphicsConfiguration[] configs = screen.getConfigurations();
			for (GraphicsConfiguration gc : configs) {
				if (gc.getBounds().contains(x, y)) return true;
			}
		}
		return false;
	}
	
	private int getInt(String theString, int defaultValue) {
		if (theString == null) return defaultValue;
		theString = theString.trim();
		if (theString.equals("")) return defaultValue;
		try { return Integer.parseInt(theString); }
		catch (NumberFormatException e) { return defaultValue; }
	}

}
