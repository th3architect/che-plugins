/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.extension.machine.client.machine;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.machine.gwt.client.MachineServiceClient;
import org.eclipse.che.api.machine.shared.dto.MachineDescriptor;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.app.CurrentProject;
import org.eclipse.che.ide.api.event.ProjectActionEvent;
import org.eclipse.che.ide.api.event.ProjectActionHandler;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.parts.WorkspaceAgent;
import org.eclipse.che.ide.extension.machine.client.MachineLocalizationConstant;
import org.eclipse.che.ide.extension.machine.client.MachineResources;
import org.eclipse.che.ide.extension.machine.client.OutputMessageUnmarshaller;
import org.eclipse.che.ide.extension.machine.client.command.CommandConfiguration;
import org.eclipse.che.ide.extension.machine.client.machine.console.MachineConsolePresenter;
import org.eclipse.che.ide.extension.machine.client.outputspanel.OutputsContainerPresenter;
import org.eclipse.che.ide.extension.machine.client.outputspanel.console.CommandConsoleFactory;
import org.eclipse.che.ide.extension.machine.client.outputspanel.console.OutputConsole;
import org.eclipse.che.ide.util.UUID;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.websocket.MessageBus;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.ide.websocket.rest.SubscriptionHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Manager for machine operations.
 *
 * @author Artem Zatsarynnyy
 */
@Singleton
public class MachineManager implements ProjectActionHandler {

    private final MachineResources            machineResources;
    private final MachineServiceClient        machineServiceClient;
    private final MessageBus                  messageBus;
    private final MachineConsolePresenter     machineConsolePresenter;
    private final OutputsContainerPresenter   outputsContainerPresenter;
    private final CommandConsoleFactory       commandConsoleFactory;
    private final NotificationManager         notificationManager;
    private final MachineLocalizationConstant localizationConstant;
    private final WorkspaceAgent              workspaceAgent;
    private final MachineStateNotifier        machineStateNotifier;
    private final AppContext                  appContext;

    private String currentMachineId;

    @Inject
    public MachineManager(MachineResources machineResources,
                          MachineServiceClient machineServiceClient,
                          MessageBus messageBus,
                          MachineConsolePresenter machineConsolePresenter,
                          OutputsContainerPresenter outputsContainerPresenter,
                          CommandConsoleFactory commandConsoleFactory,
                          NotificationManager notificationManager,
                          MachineLocalizationConstant localizationConstant,
                          WorkspaceAgent workspaceAgent,
                          MachineStateNotifier machineStateNotifier,
                          AppContext appContext) {
        this.machineResources = machineResources;
        this.machineServiceClient = machineServiceClient;
        this.messageBus = messageBus;
        this.machineConsolePresenter = machineConsolePresenter;
        this.outputsContainerPresenter = outputsContainerPresenter;
        this.commandConsoleFactory = commandConsoleFactory;
        this.notificationManager = notificationManager;
        this.localizationConstant = localizationConstant;
        this.workspaceAgent = workspaceAgent;
        this.machineStateNotifier = machineStateNotifier;
        this.appContext = appContext;
    }

    @Override
    public void onProjectOpened(ProjectActionEvent event) {
        final String projectPath = event.getProject().getPath();
        machineServiceClient.getMachines(projectPath).then(new Operation<List<MachineDescriptor>>() {
            @Override
            public void apply(List<MachineDescriptor> arg) throws OperationException {
                if (arg.isEmpty()) {
                    startMachine(true);
                } else {
                    currentMachineId = arg.get(0).getId();
                }
            }
        });
    }

    @Override
    public void onProjectClosing(ProjectActionEvent event) {
    }

    @Override
    public void onProjectClosed(ProjectActionEvent event) {
        currentMachineId = null;
        machineConsolePresenter.clear();
    }

    /** Start machine and set it as current machine if {@code asCurrent} is {@code true}. */
    public void startMachine(final boolean asCurrent) {
        final String recipeScript = machineResources.testDockerRecipe().getText();
        final String outputChannel = "machine:output:" + UUID.uuid();
        subscribeToOutput(outputChannel);

        final Promise<MachineDescriptor> machinePromise = machineServiceClient.createMachineFromRecipe("docker",
                                                                                                       "Dockerfile",
                                                                                                       recipeScript,
                                                                                                       outputChannel);
        machinePromise.then(new Operation<MachineDescriptor>() {
            @Override
            public void apply(final MachineDescriptor arg) throws OperationException {
                MachineStateNotifier.RunningListener runningListener = null;
                if (asCurrent) {
                    runningListener = new MachineStateNotifier.RunningListener() {
                        @Override
                        public void onRunning() {
                            setCurrentMachineId(arg.getId());
                        }
                    };
                }
                machineStateNotifier.trackMachine(arg.getId(), runningListener);
            }
        });
    }

    public void destroyMachine(final String machineId) {
        machineServiceClient.destroyMachine(machineId).then(new Operation<Void>() {
            @Override
            public void apply(Void arg) throws OperationException {
                machineStateNotifier.trackMachine(machineId);
                if (getCurrentMachineId() != null && machineId.equals(getCurrentMachineId())) {
                    currentMachineId = null;
                }
            }
        });
    }

    /** Returns ID of the current machine (where current project is bound). */
    @Nullable
    public String getCurrentMachineId() {
        return currentMachineId;
    }

    /** Sets ID of the current machine (where current project should be bound). */
    public void setCurrentMachineId(@Nonnull final String machineId) {
        final CurrentProject currentProject = appContext.getCurrentProject();
        if (currentProject == null) {
            return;
        }
        machineServiceClient.bindProject(machineId, currentProject.getRootProject().getPath()).then(new Operation<Void>() {
            @Override
            public void apply(Void arg) throws OperationException {
                currentMachineId = machineId;
                notificationManager.showInfo(localizationConstant.currentMachineChanged(currentMachineId));
            }
        });
    }

    private void subscribeToOutput(final String channel) {
        try {
            messageBus.subscribe(
                    channel,
                    new SubscriptionHandler<String>(new OutputMessageUnmarshaller()) {
                        @Override
                        protected void onMessageReceived(String result) {
                            machineConsolePresenter.print(result);
                        }

                        @Override
                        protected void onErrorReceived(Throwable exception) {
                            notificationManager.showError(exception.getMessage());
                        }
                    });
        } catch (WebSocketException e) {
            Log.error(MachineManager.class, e);
            notificationManager.showError(e.getMessage());
        }
    }

    /** Execute the the given command configuration on current machine. */
    public void execute(@Nonnull CommandConfiguration configuration) {
        final String currentMachineId = getCurrentMachineId();
        if (currentMachineId == null) {
            notificationManager.showWarning(localizationConstant.noCurrentMachine());
            return;
        }

        final String outputChannel = "process:output:" + UUID.uuid();

        final OutputConsole console = commandConsoleFactory.create(configuration);
        console.attachToOutput(outputChannel);
        outputsContainerPresenter.addConsole(console);
        workspaceAgent.setActivePart(outputsContainerPresenter);

        machineServiceClient.executeCommand(currentMachineId, configuration.toCommandLine(), outputChannel);
    }
}
