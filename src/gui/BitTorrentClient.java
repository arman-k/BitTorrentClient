/**
 * Graphical User Interface for the BitTorrent client
 */

package gui;

import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.ActionEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import client.Environment;
import tools.Util;
import torrent.Torrent;
import torrent.TorrentManager;

import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.JFileChooser;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

public class BitTorrentClient implements TorrentEventListener {

	private JFrame frame;
	private JFileChooser fileChooser;
	private TorrentManager torrentManager;
	DefaultTableModel tableModel;
	private final static int STATUS = 2;
	private final static int PROGRESS = 3;
	private JTable table;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					BitTorrentClient window = new BitTorrentClient();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public BitTorrentClient() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		initTorrentManager();

		frame = new JFrame("BitTorrent Client");
		frame.setBounds(100, 100, 800, 600);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				if (JOptionPane.showConfirmDialog(frame, "Are you sure you want to exit?", "Confirm closing",
						JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
					torrentManager.exit();
					System.exit(0);
				}
			}
		});

		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);

		JMenu mnNewMenu = new JMenu("File");
		menuBar.add(mnNewMenu);

		JMenuItem mntmAddNewTorrent = new JMenuItem("Add torrent");
		mntmAddNewTorrent.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				fileChooser = new JFileChooser();
				FileNameExtensionFilter filter = new FileNameExtensionFilter("Torrent meta-files", "torrent");
				fileChooser.setFileFilter(filter);
				fileChooser.showOpenDialog(null);
				if (fileChooser.getSelectedFile() != null)
					loadTorrent(fileChooser.getSelectedFile().getName());
			}
		});
		mnNewMenu.add(mntmAddNewTorrent);

		JMenuItem mntmExit = new JMenuItem("Exit");
		mntmExit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
			}
		});
		mnNewMenu.add(mntmExit);

		JMenu mnPreferences = new JMenu("Options");
		menuBar.add(mnPreferences);

		JMenuItem mntmPreferences = new JMenuItem("Preferences");
		mntmPreferences.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String s = (String) JOptionPane.showInputDialog(frame, "Choose root download directory:", "Preferences",
						JOptionPane.PLAIN_MESSAGE, null, null, Environment.getInstance().getRootDownloadDirectory());

				if ((s != null) && (s.length() > 0)) {
					Environment.getInstance().setRootDownloadDirectory(s);
					return;
				}
			}
		});
		mnPreferences.add(mntmPreferences);

		JMenu mnNewMenu_1 = new JMenu("Help");
		menuBar.add(mnNewMenu_1);

		JMenuItem mntmAbout = new JMenuItem("About");
		mntmAbout.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JOptionPane.showMessageDialog(frame,
						"BitTorrent Client Beta v0.01\nPeer id for current session: "
								+ Util.URLEncode(Environment.getInstance().getPeerId().array()),
						"About", JOptionPane.INFORMATION_MESSAGE);
			}
		});
		mnNewMenu_1.add(mntmAbout);
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		gridBagLayout.rowHeights = new int[] { 0, 0, 0 };
		gridBagLayout.columnWeights = new double[] { 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
				0.0, 0.0, Double.MIN_VALUE };
		gridBagLayout.rowWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
		frame.getContentPane().setLayout(gridBagLayout);

		JButton btnStart = new JButton("Start");
		btnStart.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				if (table.getSelectedRow() > -1 && table.getModel().getValueAt(table.getSelectedRow(), 0) != null) {
					tableModel.setValueAt("Starting", table.getSelectedRow(), STATUS);
					tableModel.setValueAt("0.00%", table.getSelectedRow(), PROGRESS);
					torrentManager.startTorrent(table.getSelectedRow());
				}
			}
		});
		GridBagConstraints gbc_btnStart = new GridBagConstraints();
		gbc_btnStart.insets = new Insets(0, 0, 5, 5);
		gbc_btnStart.gridx = 2;
		gbc_btnStart.gridy = 0;
		frame.getContentPane().add(btnStart, gbc_btnStart);
		btnStart.setEnabled(false);

		JButton btnStop = new JButton("Stop");
		btnStop.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				if (table.getSelectedRow() > -1 && table.getModel().getValueAt(table.getSelectedRow(), 0) != null) {
					torrentManager.stopTorrent(table.getSelectedRow());
					tableModel.setValueAt("Stopped", table.getSelectedRow(), STATUS);
				}
			}
		});
		GridBagConstraints gbc_btnStop = new GridBagConstraints();
		gbc_btnStop.insets = new Insets(0, 0, 5, 5);
		gbc_btnStop.gridx = 5;
		gbc_btnStop.gridy = 0;
		frame.getContentPane().add(btnStop, gbc_btnStop);
		btnStop.setEnabled(false);

		JButton btnRemove = new JButton("Remove");
		btnRemove.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				if (table.getSelectedRow() > -1 && table.getModel().getValueAt(table.getSelectedRow(), 0) != null) {
					torrentManager.removeTorrent(table.getSelectedRow());
					tableModel.removeRow(table.getSelectedRow());
				}
			}
		});
		GridBagConstraints gbc_btnRemove = new GridBagConstraints();
		gbc_btnRemove.insets = new Insets(0, 0, 5, 5);
		gbc_btnRemove.gridx = 8;
		gbc_btnRemove.gridy = 0;
		frame.getContentPane().add(btnRemove, gbc_btnRemove);
		btnRemove.setEnabled(false);

		JButton btnMetainfo = new JButton("Metainfo");
		btnMetainfo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			}
		});
		btnMetainfo.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				if (table.getSelectedRow() > -1 && table.getModel().getValueAt(table.getSelectedRow(), 0) != null) {
					JOptionPane.showMessageDialog(frame, torrentManager.printMetainfo(table.getSelectedRow()),
							"Metainfo", JOptionPane.INFORMATION_MESSAGE);
				}
			}
		});
		GridBagConstraints gbc_btnMetainfo = new GridBagConstraints();
		gbc_btnMetainfo.insets = new Insets(0, 0, 5, 5);
		gbc_btnMetainfo.gridx = 11;
		gbc_btnMetainfo.gridy = 0;
		frame.getContentPane().add(btnMetainfo, gbc_btnMetainfo);
		btnMetainfo.setEnabled(false);

		tableModel = new DefaultTableModel() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public boolean isCellEditable(int row, int col) {
				return false;
			}
		};

		table = new JTable(tableModel);
		((DefaultTableModel) table.getModel()).addColumn("Name");
		((DefaultTableModel) table.getModel()).addColumn("Size (MB)");
		((DefaultTableModel) table.getModel()).addColumn("Status");
		((DefaultTableModel) table.getModel()).addColumn("Progress");
		table.setFillsViewportHeight(true);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent event) {
				if (table.getSelectedRow() > -1) {
					btnStart.setEnabled(true);
					btnRemove.setEnabled(true);
					btnStop.setEnabled(true);
					btnMetainfo.setEnabled(true);
				}
			}
		});

		JScrollPane scrollPane = new JScrollPane(table);
		GridBagConstraints gbc_scrollPane = new GridBagConstraints();
		gbc_scrollPane.gridwidth = 13;
		gbc_scrollPane.insets = new Insets(0, 0, 0, 5);
		gbc_scrollPane.fill = GridBagConstraints.BOTH;
		gbc_scrollPane.gridx = 1;
		gbc_scrollPane.gridy = 1;
		frame.getContentPane().add(scrollPane, gbc_scrollPane);
	}

	private void initTorrentManager() {
		System.out.println("Initializing client...");
		// System.out
		// .println("Peer id for this session: " +
		// Util.URLEncode(Environment.getInstance().getPeerId().array()));
		torrentManager = new TorrentManager();
		torrentManager.registerForStatusEvents(this);
	}

	private void loadTorrent(String filename) {
		Torrent torrent = torrentManager.addTorrent(filename);
		if (torrent == null) {
			System.out.println("Couldn't load " + filename);
			return;
		}

		// System.out.println("Torrent \"" + filename + "\" added!");

		tableModel.addRow(new Object[] { torrent.getName(),
				String.format("%.2f", ((double) torrent.getSize() / Util.SIZE_MB)), "Loaded" });
	}

	@Override
	public void onStatusUpdated(int torrentIndex, String status) {
		tableModel.setValueAt(status, torrentIndex, STATUS);
	}

	public void onProgressMade(int torrentIndex, Double progress) {
		tableModel.setValueAt(String.format("%.2f%%", progress), torrentIndex, PROGRESS);
	}
}
