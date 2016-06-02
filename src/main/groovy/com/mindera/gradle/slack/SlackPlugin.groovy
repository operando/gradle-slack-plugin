package com.mindera.gradle.slack

import com.mindera.gradle.slack.model.SlackMessageTransformer
import net.gpedro.integrations.slack.SlackApi
import net.gpedro.integrations.slack.SlackMessage
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.logging.StandardOutputListener
import org.gradle.api.tasks.TaskState

/**
 * Created by joaoprudencio on 05/05/15.
 */
class SlackPlugin implements Plugin<Project> {

    SlackPluginExtension extension
    StringBuilder taskLogBuilder

    void apply(Project project) {

        taskLogBuilder = new StringBuilder()
        extension = project.extensions.create('slack', SlackPluginExtension)

        project.afterEvaluate {
            if (extension.url != null && extension.enabled)
                monitorTasksLifecyle(project)
        }
    }

    void monitorTasksLifecyle(Project project) {
        project.getGradle().getTaskGraph().addTaskExecutionListener(new TaskExecutionListener() {

            @Override
            void beforeExecute(final Task task) {
                if(!shouldMonitorTask(task)){
                    return;
                }
                task.logging.addStandardOutputListener(new StandardOutputListener() {
                    @Override
                    void onOutput(CharSequence charSequence) {
                        taskLogBuilder.append(charSequence);
                    }
                })
            }

            @Override
            void afterExecute(Task task, TaskState state) {
                handleTaskFinished(task, state, taskLogBuilder.toString())
                taskLogBuilder.delete(0, taskLogBuilder.length())
            }
        })
    }

    void handleTaskFinished(Task task, TaskState state, String outputMessage) {
        boolean shouldSendMessage = shouldMonitorTask(task);

        // only send a slack message if the task failed
        // or the task is registered to be monitored
        if (shouldSendMessage) {
            SlackMessage slackMessage = SlackMessageTransformer.buildSlackMessage(
                    extension.title, task, state, outputMessage, extension.gitInfo)
            SlackApi api = new SlackApi(extension.url)
            api.call(slackMessage)
        }
    }

    boolean shouldMonitorTask(Task task) {
        for (dependentTask in extension.dependsOnTasks) {
            if (task.getName().equals(dependentTask)) {
                return true
            }
        }
        return false
    }
}