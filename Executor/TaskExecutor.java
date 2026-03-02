package Executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import Node.Base64Thread;

public class TaskExecutor {
    public static void main(String[] args) {
        // creating the runnable
        Base64Thread task1 = new Base64Thread("hello");

        System.out.println("Starting Executor.");

        // creating ExecutorService to manage the above threads
        ExecutorService executorService = Executors.newCachedThreadPool();

        // starts the tasks
        executorService.execute(task1);

        // shuts down executorService
        executorService.shutdown();

        System.out.printf("Tasks started, main ends.%n%n");
    }
}
