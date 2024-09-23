package searchengine.services.assets;

import java.util.concurrent.atomic.AtomicBoolean;

public class IndexingTask implements Runnable {

    private final Runnable target;

    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread worker;

    public IndexingTask(Runnable target) {
        this.target = target;
    }

    public void start() {
        worker = new Thread(this);
        worker.start();
    }

    public void stop() {
        isRunning.set(false);
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public void run() {
        isRunning.set(true);
        while(isRunning.get()) {
            try {
                Thread.sleep(1);
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("IndexingTask was aborted, operation is halted");
            }

            target.run();
            isRunning.set(false);

        }
    }
}
