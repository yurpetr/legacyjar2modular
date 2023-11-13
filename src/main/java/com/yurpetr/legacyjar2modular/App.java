package com.yurpetr.legacyjar2modular;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class App {

    public enum DragState {

        Waiting, Accept, Reject
    }

    public static void main(String[] args) {

        new App();

    }

    public App() {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException
                        | UnsupportedLookAndFeelException ex) {
                    ex.printStackTrace();
                }

                JFrame frame = new JFrame();
                frame.setLayout(new BorderLayout());
                frame.setTitle("Legacy JAR converter");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(400, 250);
                frame.setResizable(false);
                frame.setLocationRelativeTo(null);
                frame.setAlwaysOnTop(true);
                frame.setVisible(true);
                frame.add(new DropPane(), BorderLayout.SOUTH);
                frame.pack();
            }
        });
    }

    class DropPane extends JPanel {

        public DropPane() {
            setLayout(new GridLayout(1, 1, 5, 5));

            Font               font  = new Font("Arial", 0, 25);
            SimpleAttributeSet align = new SimpleAttributeSet();
            StyleConstants.setAlignment(align, StyleConstants.ALIGN_CENTER);

            DropArea dropJar = new DropArea(font, align);

            add(dropJar);

        }

    }

    class DropArea extends JTextPane implements DropTargetListener {

        /**
         * 
         */

        private DragState state = DragState.Waiting;

        protected DropArea(Font font, SimpleAttributeSet align) {
            super();
            new DropTarget(this, this);
            setDragEnabled(true);
            setEditable(false);
            setFocusable(false);
            setHighlighter(null);
            Border         dashedBorder   = BorderFactory.createDashedBorder(Color.GRAY, 4f, 6.5f,
                    2f, true);
            CompoundBorder compoundBorder = BorderFactory.createCompoundBorder(dashedBorder,
                    BorderFactory.createEmptyBorder(50, 20, 50, 20));
            setBorder(compoundBorder);
            setFont(font);
            setForeground(Color.GRAY);
            setText("Drop JAR here");

            StyledDocument style = getStyledDocument();
            style.setParagraphAttributes(0, style.getLength(), align, false);
        }

        @Override
        public void dragEnter(DropTargetDragEvent dtde) {
            state = DragState.Reject;
            Transferable t = dtde.getTransferable();
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    Object td = t.getTransferData(DataFlavor.javaFileListFlavor);
                    if (td instanceof List) {
                        state = DragState.Accept;
                        for (Object value : ((List) td)) {
                            if (value instanceof File) {
                                File   file = (File) value;
                                String name = file.getName().toLowerCase();
                                if (!name.endsWith(".jar")) {
                                    state = DragState.Reject;
                                    break;
                                }
                            }
                        }
                    }
                } catch (UnsupportedFlavorException | IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (state == DragState.Accept) {
                dtde.acceptDrag(DnDConstants.ACTION_NONE);
            } else {
                dtde.rejectDrag();
            }
        }

        @Override
        public void dragOver(DropTargetDragEvent dtde) {

        }

        @Override
        public void dropActionChanged(DropTargetDragEvent dtde) {

        }

        @Override
        public void dragExit(DropTargetEvent dte) {

        }

        @Override
        public void drop(DropTargetDropEvent dtde) {
            state = DragState.Reject;
            Transferable t = dtde.getTransferable();
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Object td = t.getTransferData(DataFlavor.javaFileListFlavor);
                    if (td instanceof List) {
                        state = DragState.Accept;
                        for (Object value : ((List) td)) {
                            if (value instanceof File) {
                                repackJar((File) value);
                            }
                        }
                    }
                } catch (UnsupportedFlavorException e) {
                    System.out.println("unsupported flavor exception");
                } catch (IOException e) {
                    System.out.println("cant read files");
                }
            }

        }

        private void repackJar(File value) {

            String javaHome = System.getProperty("java.home") + "\\bin\\";

            try {
                Path tempDirectory = Files.createTempDirectory(value.getName());

                String outputDir = tempDirectory.toString();
                System.out.println(outputDir);

                String   jdepsPath    = "jdeps.exe";
                String[] jdepsCommand = { "powershell.exe", javaHome + jdepsPath,
                        "--ignore-missing-deps", "--generate-module-info", outputDir,
                        value.getAbsolutePath() };

                executeCommand(jdepsCommand);

                try (Stream<Path> stream = Files.list(tempDirectory)) {
                    Set<String> dirs = stream.filter(file -> Files.isDirectory(file))
                            .map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
                    dirs.stream().forEach(packageName -> {
                        try {

                            String   javacPath    = "javac.exe";
                            String[] javacCommand = { "powershell.exe", javaHome + javacPath,
                                    "--patch-module", packageName + "=" + value.getAbsolutePath(),
                                    outputDir + "\\" + packageName + "\\" + "module-info.java" };

                            executeCommand(javacCommand);

                            String   jarPath    = "jar.exe";
                            String[] jarCommand = { "powershell.exe", javaHome + jarPath, "uf",
                                    value.getAbsolutePath(), "-C", outputDir + "\\" + packageName,
                                    "module-info.class" };

                            executeCommand(jarCommand);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    });

                }

                Files.walk(tempDirectory).sorted(Comparator.reverseOrder()).map(Path::toFile)
                        .forEach(File::delete);

            } catch (IOException e) {

                e.printStackTrace();
            }

        }

        private void executeCommand(String[] command) throws IOException {
            Process powerShellProcess = new ProcessBuilder(command).start();
            powerShellProcess.getOutputStream().close();

            List<String> list = new ArrayList<>();
            String       line;

            System.out.println("Standard Output:");
            BufferedReader stdout = new BufferedReader(
                    new InputStreamReader(powerShellProcess.getInputStream()));
            while ((line = stdout.readLine()) != null) {
                list.add(line);
                System.out.println(line);
            }
            stdout.close();

            System.out.println("Standard Error:");
            BufferedReader stderr = new BufferedReader(
                    new InputStreamReader(powerShellProcess.getErrorStream()));
            while ((line = stderr.readLine()) != null) {
                System.out.println(line);
            }
            stderr.close();

            System.out.println("Done");
        }

    }

}
