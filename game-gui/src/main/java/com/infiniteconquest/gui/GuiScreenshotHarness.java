package com.infiniteconquest.gui;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Deterministic visual fixture runner used by CI under Xvfb. */
public final class GuiScreenshotHarness {
    private static final String[] STATIC_SCENARIOS = {"opening-board", "selected-hand", "expanded-hand", "crowded-board", "terrain-board"};

    private GuiScreenshotHarness() { }

    public static void main(String[] args) {
        try { runCapture(args); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }

    private static void runCapture(String[] args) throws Exception {
        Path outputDirectory = Path.of(args.length == 0 ? "build/screenshots" : args[0]);
        for (int[] size : new int[][] {{1280, 650}, {1366, 768}, {1920, 1080}, {1100, 700}}) {
            for (String scenario : STATIC_SCENARIOS) {
                SwingUtilities.invokeAndWait(() -> capture(outputDirectory, scenario, size[0], size[1]));
            }
        }
        captureMotion(outputDirectory, "deployment-motion", 145);
        captureMotion(outputDirectory, "invalid-drop-motion", 90);
        captureMotion(outputDirectory, "board-movement", 160);
        captureMotion(outputDirectory, "melee-lunge", 180);
        captureMotion(outputDirectory, "card-destruction", 150);
        SwingUtilities.invokeAndWait(() -> {
            InfiniteConquestGui gui = new InfiniteConquestGui(true);
            try {
                gui.captureOpeningScreens(outputDirectory);
                var factory = new com.infiniteconquest.cli.DemoMatchFactory();
                var build = new com.infiniteconquest.core.DeckBuild("Review", "ZEUS", "POSEIDON", factory.capitals().forFaction("ZEUS").get(0), new com.infiniteconquest.cli.FactionDecks(factory.pool()).starter("ZEUS"));
                for (int step=0;step<4;step++) new DeckBuilderDialog(gui,factory.pool(),factory.capitals(),build).captureForReview(step,outputDirectory.resolve("deck-builder-step-"+step+".png"));
            } finally { gui.dispose(); }
        });
        SwingUtilities.invokeAndWait(() -> {
            for(var skin:InitiativeCoinPanel.Skin.values())for(int winner=0;winner<2;winner++) {
                InitiativeCoinPanel coin = new InitiativeCoinPanel(winner,skin);
                coin.setSize(460,410);
                for(int frame=0;frame<=8;frame++) {
                    coin.setProgress(frame/8.0);
                    var image = new java.awt.image.BufferedImage(460,410,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var graphics=image.createGraphics();coin.paint(graphics);graphics.dispose();
                    try { javax.imageio.ImageIO.write(image,"png",outputDirectory.resolve("coin-"+skin.name()+"-player-"+(winner+1)+"-frame-"+frame+".png").toFile()); }
                    catch(java.io.IOException e) { throw new IllegalStateException(e); }
                }
            }
        });
        verifyReactionReview(outputDirectory);
        verifyAutomatedPlayback(outputDirectory);
        System.exit(0);
    }

    private static void verifyReactionReview(Path directory) throws Exception {
        CountDownLatch finished=new CountDownLatch(1);
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(()->{
            InfiniteConquestGui gui=new InfiniteConquestGui(true);prepare(gui,"opening-board");gui.prepareCaptureSize(1280,650,true);
            gui.prepareReactionReview(directory);
            String[] frozen={null};long[] opened={0};long start=System.nanoTime();
            Timer monitor=new Timer(25,event->{
                try {
                    var dialog=gui.visibleReactionReview();
                    if(dialog!=null){
                        if(frozen[0]==null){frozen[0]=gui.captureStateFingerprint();opened[0]=System.nanoTime();}
                        if(!frozen[0].equals(gui.captureStateFingerprint()))throw new IllegalStateException("Bot advanced while reaction dialog was open");
                        if(System.nanoTime()-opened[0]>650_000_000L){((Timer)event.getSource()).stop();dialog.dispose();gui.dispose();finished.countDown();}
                    }
                    if(System.nanoTime()-start>5_000_000_000L)throw new IllegalStateException("Reaction window did not open");
                }catch(Throwable error){failure.set(error);((Timer)event.getSource()).stop();for(var w:gui.getOwnedWindows())w.dispose();gui.dispose();finished.countDown();}
            });monitor.start();gui.beginReactionReview();
        });
        if(!finished.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Reaction review timed out");
        if(failure.get()!=null)throw new IllegalStateException("Reaction review failed",failure.get());
    }

    private static void verifyAutomatedPlayback(Path outputDirectory) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            InfiniteConquestGui gui = new InfiniteConquestGui(true);
            prepare(gui, "opening-board");
            gui.prepareCaptureSize(1280,650,true);
            gui.beginAutomatedCapture();
            long started = System.nanoTime();
            PresentationSnapshot[] previous = {null};
            String[] fingerprint = {""};
            int[] samples = {0};
            int[] actions = {0};
            Timer monitor = new Timer(16, event -> {
                try {
                    var active = gui.captureActivePresentation();
                    String current = gui.captureStateFingerprint();
                    if(active != null) {
                        samples[0]++;
                        if(active == previous[0] && !current.equals(fingerprint[0]))
                            throw new IllegalStateException("Bot changed game state before the active presentation completed");
                        if(active != previous[0]) actions[0]++;
                    }
                    previous[0] = active; fingerprint[0] = current;
                    if (System.nanoTime()-started > 10_000_000_000L) {
                        if(samples[0]<20 || actions[0]<4) throw new IllegalStateException("Bot review did not exercise enough animated actions");
                        gui.prepareCaptureSize(1280,650,true);
                        gui.captureScreenshot(outputDirectory.resolve("bot-playback.png"));
                        ((Timer)event.getSource()).stop();gui.dispose();finished.countDown();
                    }
                } catch(Throwable error) {
                    failure.set(error);((Timer)event.getSource()).stop();gui.dispose();finished.countDown();
                }
            });
            monitor.start();
        });
        if(!finished.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Bot playback review timed out");
        if(failure.get()!=null)throw new IllegalStateException("Bot playback review failed",failure.get());
    }

    private static void capture(Path outputDirectory, String scenario, int width, int height) {
        InfiniteConquestGui gui = new InfiniteConquestGui(true);
        try {
            prepare(gui, scenario);
            gui.prepareCaptureSize(width, height, !scenario.equals("expanded-hand"));
            if(scenario.equals("opening-board"))gui.verifyHandOverlay();
            gui.captureScreenshot(outputDirectory.resolve(scenario + "-" + width + "x" + height + ".png"));
        } finally {
            gui.dispose();
        }
    }

    private static void captureMotion(Path outputDirectory, String scenario, int delayMs) throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            InfiniteConquestGui gui = new InfiniteConquestGui(true);
            prepare(gui, scenario);
            Timer midpoint = new Timer(delayMs, event -> {
                try {
                    gui.prepareCaptureSize(1366, 768, false);
                    gui.captureScreenshot(outputDirectory.resolve(scenario + ".png"));
                } finally {
                    gui.dispose();
                    captured.countDown();
                }
            });
            midpoint.setRepeats(false);
            midpoint.start();
        });
        if (!captured.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out capturing " + scenario);
        }
    }

    private static void prepare(InfiniteConquestGui gui, String scenario) {
        gui.setSize(1500, 980);
        gui.setLocationRelativeTo(null);
        gui.setVisible(true);
        gui.prepareScreenshotScenario(scenario);
    }
}
