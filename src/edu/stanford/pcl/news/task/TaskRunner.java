package edu.stanford.pcl.news.task;


import java.lang.reflect.Type;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.pcl.news.NewsProperties;

public abstract class TaskRunner {
    protected RemoteTaskServer server;
    private List<TaskWorker> workers = new ArrayList<TaskWorker>();
    protected Map<Type, List<TaskResolver>> resolvers = new HashMap<Type, List<TaskResolver>>();


    public TaskRunner() {
        server = new TaskServer();
    }

    protected TaskRunner(TaskServer server) {
        this.server = server;
    }


    public void registerWorker(TaskWorker worker) {
        workers.add(worker);
    }

    public void registerResolver(Type taskType, TaskResolver resolver) {
        List<TaskResolver> resolverList = this.resolvers.get(taskType);
        if (resolverList == null) {
            resolverList = new ArrayList<TaskResolver>();
            this.resolvers.put(taskType, resolverList);
        }
        resolverList.add(resolver);
    }

    public abstract Task next();

    public void start() throws RemoteException {
        if (this.server == null) {
            throw new IllegalStateException("No TaskServer has been registered.");
        }

        for (Map.Entry<Type, List<TaskResolver>> entry : resolvers.entrySet()) {
            for (TaskResolver resolver : entry.getValue()) {
                server.registerResolver(entry.getKey(), resolver);
            }
        }

        // XXX  Remove this stuff and only use RMI when necessary.
//            System.setProperty("java.rmi.server.codebase", NewsProperties.getProperty("rmi.registry.codebase"));
//            System.setProperty("java.rmi.server.hostname", NewsProperties.getProperty("rmi.registry.hostname"));
        Registry registry = LocateRegistry.createRegistry(Integer.parseInt(NewsProperties.getProperty("rmi.registry.port")));
        registry.rebind("TaskServer", UnicastRemoteObject.exportObject(server, Integer.parseInt(NewsProperties.getProperty("rmi.server.port"))));

        for (TaskWorker worker : workers) {
            worker.register(NewsProperties.getProperty("rmi.registry.hostname"));
            worker.start();
        }

        Task task;
        while ((task = next()) != null) {
            server.putTask(task);
        }

        // XXX  Need to put this somewhere.
//        UnicastRemoteObject.unexportObject(server, false);

        server.putTask(new TerminateTask());

    }
}
