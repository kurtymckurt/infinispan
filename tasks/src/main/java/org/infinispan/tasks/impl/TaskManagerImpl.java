package org.infinispan.tasks.impl;

import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import javax.security.auth.Subject;

import org.infinispan.commons.util.CollectionFactory;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.security.Security;
import org.infinispan.tasks.TaskContext;
import org.infinispan.tasks.TaskExecution;
import org.infinispan.tasks.TaskManager;
import org.infinispan.tasks.logging.Log;
import org.infinispan.tasks.spi.TaskEngine;
import org.infinispan.util.TimeService;
import org.infinispan.util.logging.LogFactory;

/**
 * TaskManagerImpl.
 *
 * @author Tristan Tarrant
 * @since 8.1
 */
@Scope(Scopes.GLOBAL)
public class TaskManagerImpl implements TaskManager {
   private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass(), Log.class);
   private EmbeddedCacheManager cacheManager;
   private Set<TaskEngine> engines;
   private ConcurrentMap<UUID, TaskExecution> runningTasks;
   private TimeService timeService;
   private boolean useSecurity;

   public TaskManagerImpl() {
      engines = new HashSet<>();
      runningTasks = CollectionFactory.makeConcurrentMap();
   }

   @Inject
   public void initialize(final EmbeddedCacheManager cacheManager, final TimeService timeService) {
      this.cacheManager = cacheManager;
      this.timeService = timeService;
      this.useSecurity = cacheManager.getCacheManagerConfiguration().security().authorization().enabled();
   }

   public synchronized void registerTaskEngine(TaskEngine engine) {
      if (engines.contains(engine)) {
         throw log.duplicateTaskEngineRegistration(engine.getName());
      } else {
         engines.add(engine);
      }
   }

   @Override
   public <T> CompletableFuture<T> runTask(String name, TaskContext context) {
      for(TaskEngine engine : engines) {
         if (engine.handles(name)) {
            Address address = cacheManager.getAddress();
            Optional<String> who;
            if (useSecurity) {
               Subject subject = Security.getSubject();
               Principal userPrincipal = Security.getSubjectUserPrincipal(subject);
               who = Optional.of(userPrincipal.getName());
            } else {
               who = Optional.empty();
            }
            TaskExecutionImpl exec = new TaskExecutionImpl(name, address == null ? "local" : address.toString(), who, context);
            exec.setStart(timeService.instant());
            runningTasks.put(exec.getUUID(), exec);
            CompletableFuture<T> task = engine.runTask(name, context);
            return task.whenComplete((r, e) -> {
               // TODO: hook up to the event logger, once that is implemented
               if (e != null) {
                  e.printStackTrace();
               }
               runningTasks.remove(exec.getUUID());
            });
         }
      }
      throw log.unknownTask(name);
   }

   @Override
   public Collection<TaskExecution> getCurrentTasks() {
      return runningTasks.values();
   }

   @Override
   public Collection<TaskEngine> getEngines() {
      return Collections.unmodifiableCollection(engines);
   }

}
