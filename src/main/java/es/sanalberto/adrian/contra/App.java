package es.sanalberto.adrian.contra;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.BufferedInputStream;
import java.io.InputStream;

import javafx.stage.Stage;

public class App extends Application {

    private Clip bgmClip;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(Game.W, Game.H);
        startBackgroundMusic();

        Game gamekk = new Game(canvas);

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        scene.setOnKeyPressed(e -> gamekk.keyPressed(e.getCode()));
        scene.setOnKeyReleased(e -> gamekk.keyReleased(e.getCode()));

        stage.setTitle("Prototipo Run & Gun");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        canvas.requestFocus();

        AnimationTimer timer = new AnimationTimer() {
            private long last = 0;

            @Override
            public void handle(long now) {
                if (last == 0) {
                    last = now;
                }

                double dt = (now - last) / 1_000_000_000.0;
                last = now;

                gamekk.update(dt);
                gamekk.render();
            }
        };

        timer.start();
    }

    private void startBackgroundMusic() {
        try {
            InputStream audioSrc = getClass().getResourceAsStream("/audio/contra_returns_loop.wav");
            if (audioSrc == null) {
                System.err.println("No se encontró el archivo de audio: /audio/contra_returns_loop.wav");
                return;
            }
            InputStream bufferedIn = new BufferedInputStream(audioSrc);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);
            bgmClip = AudioSystem.getClip();
            bgmClip.open(audioStream);
            bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
            bgmClip.start();
        } catch (Exception e) {
            System.err.println("Error al reproducir música de fondo: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (bgmClip != null) {
            bgmClip.stop();
            bgmClip.close();
        }
    }
}