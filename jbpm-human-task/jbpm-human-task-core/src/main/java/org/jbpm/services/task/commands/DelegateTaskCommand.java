/*
 * Copyright 2012 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.services.task.commands;

import javax.enterprise.util.AnnotationLiteral;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.jboss.seam.transaction.Transactional;
import org.jbpm.services.task.events.AfterTaskDelegatedEvent;
import org.jbpm.services.task.events.BeforeTaskDelegatedEvent;
import org.jbpm.services.task.exception.PermissionDeniedException;
import org.kie.api.task.model.OrganizationalEntity;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.User;
import org.kie.internal.command.Context;
import org.kie.internal.task.api.model.InternalTaskData;

/**
*Operation.Delegate 
        : [ new OperationCommand().{ 
                status = [ Status.Ready ],
                allowed = [ Allowed.PotentialOwner, Allowed.BusinessAdministrator  ],
                addTargetUserToPotentialOwners = true,            
                newStatus = Status.Ready,
                exec = Operation.Claim
            },
            new OperationCommand().{ 
                status = [ Status.Reserved, Status.InProgress ],
                allowed = [ Allowed.Owner, Allowed.BusinessAdministrator ],
                addTargetUserToPotentialOwners = true,                         
                newStatus = Status.Ready,
                exec = Operation.Claim
            } ],
 */
@Transactional
@XmlRootElement(name="delegate-task-command")
@XmlAccessorType(XmlAccessType.NONE)
public class DelegateTaskCommand extends TaskCommand<Void> {

	public DelegateTaskCommand() {
	}
	
    public DelegateTaskCommand(long taskId, String userId, String targetEntityId) {
        this.taskId = taskId;
        this.userId = userId;
        this.targetEntityId = targetEntityId;
    }

    public Void execute(Context cntxt) {
        TaskContext context = (TaskContext) cntxt;
        if (context.getTaskService() != null) {
        	context.getTaskService().delegate(taskId, userId, targetEntityId);
        	return null;
        }
        Task task = context.getTaskQueryService().getTaskInstanceById(taskId);
        User user = context.getTaskIdentityService().getUserById(userId);
        OrganizationalEntity targetEntity = context.getTaskIdentityService()
                                                    .getOrganizationalEntityById(targetEntityId);
        
        context.getTaskEvents().select(new AnnotationLiteral<BeforeTaskDelegatedEvent>() {
        }).fire(task);
        // CHeck for potential Owner allowed (decorator?)
        boolean adminAllowed = CommandsUtil.isAllowed(user, getGroupsIds(), task.getPeopleAssignments().getBusinessAdministrators());
        boolean potOwnerAllowed = CommandsUtil.isAllowed(user, getGroupsIds(), task.getPeopleAssignments().getPotentialOwners());
        boolean ownerAllowed = (task.getTaskData().getActualOwner() != null && task.getTaskData().getActualOwner().equals(user));
        if (!adminAllowed && !potOwnerAllowed && !ownerAllowed) {
            String errorMessage = "The user" + user + "is not allowed to Start the task " + task.getId();
            throw new PermissionDeniedException(errorMessage);
        }
        if (potOwnerAllowed || adminAllowed ) {
            if (task.getTaskData().getStatus().equals(Status.Ready)) {

            	((InternalTaskData) task.getTaskData()).setStatus(Status.Ready);
                if ( !task.getPeopleAssignments().getPotentialOwners().contains(targetEntity)) {
                    task.getPeopleAssignments().getPotentialOwners().add(targetEntity);
                }

            }
        }
        if (ownerAllowed || adminAllowed) {
            if (task.getTaskData().getStatus().equals(Status.Reserved)
                    || task.getTaskData().getStatus().equals(Status.InProgress)) {
            	((InternalTaskData) task.getTaskData()).setStatus(Status.Ready);
                if ( !task.getPeopleAssignments().getPotentialOwners().contains(targetEntity)) {
                    task.getPeopleAssignments().getPotentialOwners().add(targetEntity);
                }
            }
        }

        context.getTaskEvents().select(new AnnotationLiteral<AfterTaskDelegatedEvent>() {
        }).fire(task);

        return null;
    }
}
