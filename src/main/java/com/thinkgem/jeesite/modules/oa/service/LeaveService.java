/**
 * There are <a href="https://github.com/thinkgem/jeesite">JeeSite</a> code generation
 */
package com.thinkgem.jeesite.modules.oa.service;

import com.google.common.collect.Maps;
import com.thinkgem.jeesite.common.persistence.Page;
import com.thinkgem.jeesite.common.service.CrudService;
import com.thinkgem.jeesite.common.utils.Collections3;
import com.thinkgem.jeesite.common.utils.StringUtils;
import com.thinkgem.jeesite.modules.act.service.ActTaskService;
import com.thinkgem.jeesite.modules.act.utils.ActUtils;
import com.thinkgem.jeesite.modules.oa.dao.LeaveDao;
import com.thinkgem.jeesite.modules.oa.entity.Leave;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 请假Service
 * @author liuj
 * @version 2013-04-05
 */
@Service
@Transactional(readOnly = true)
public class LeaveService extends CrudService<LeaveDao, Leave> {

	@Autowired
	private LeaveDao leaveDao;
	@Autowired
	private RuntimeService runtimeService;
	@Autowired
	protected TaskService taskService;
	@Autowired
	protected HistoryService historyService;
	@Autowired
	protected RepositoryService repositoryService;
	@Autowired
	private IdentityService identityService;
	@Autowired
	private ActTaskService actTaskService;

	/**
	 * 获取流程详细及工作流参数
	 * @param id
	 */
	@SuppressWarnings("unchecked")
	public Leave get(String id) {
		Leave leave = leaveDao.get(id);
		Map<String,Object> variables=null;
		HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(leave.getProcessInstanceId()).singleResult();
		if(historicProcessInstance!=null) {
			variables = Collections3.extractToMap(historyService.createHistoricVariableInstanceQuery().processInstanceId(historicProcessInstance.getId()).list(), "variableName", "value");
		} else {
			variables = runtimeService.getVariables(runtimeService.createProcessInstanceQuery().processInstanceId(leave.getProcessInstanceId()).active().singleResult().getId());
		}
		leave.setVariables(variables);
		return leave;
	}

	/**
	 * 启动流程
	 * @param leave
	 * @param variables
	 */
	@Transactional(readOnly = false)
	public void save(Leave leave, Map<String, Object> variables) {
		
		// 保存业务数据
		if (StringUtils.isBlank(leave.getId())){


			// 用来设置启动流程的人员ID，引擎会自动把用户ID保存到activiti:initiator中
			identityService.setAuthenticatedUserId(leave.getCurrentUser().getLoginName());

			// 启动流程
			String businessKey = leave.getId().toString();
			variables.put("type", "leave");
			variables.put("busId", businessKey);
			ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(ActUtils.PD_LEAVE[0], ActUtils.PD_LEAVE[0] + ":" + businessKey, variables);
			leave.setProcessInstance(processInstance);

			// 更新流程实例ID
//			leave.setProcessInstanceId(processInstance.getId());
//			leaveDao.updateProcessInstanceId(leave);

			leave.preInsert();
			leaveDao.insert(leave);

			logger.debug("start process of {key={}, bkey={}, pid={}, variables={}}", new Object[] {
					ActUtils.PD_LEAVE[0], businessKey, processInstance.getId(), variables });
		}else{
			leave.preUpdate();
			leaveDao.update(leave);

			leave.getAct().setComment(("yes".equals(leave.getAct().getFlag())?"[重申] ":"[销毁] ")+leave.getAct().getComment());

			// 完成流程任务
			Map<String, Object> vars = Maps.newHashMap();
			vars.put("reApply", "yes".equals(leave.getAct().getFlag())? true : false);
			actTaskService.complete(leave.getAct().getTaskId(), leave.getAct().getProcInsId(), leave.getAct().getComment(), vars);
		}
	}

	/**
	 * 审核审批保存
	 * @param leave
	 */
	@Transactional(readOnly = false)
	public void auditSave(Leave leave) {

		// 对不同环节的业务逻辑进行操作
		String taskDefKey = leave.getAct().getTaskDefKey();
		if ("reportBack".equals(taskDefKey)) {
			taskService.complete(leave.getAct().getTaskId());
			return;
		}

		// 设置意见
		leave.getAct().setComment(("yes".equals(leave.getAct().getFlag())?"[同意] ":"[驳回] ")+leave.getAct().getComment());

		leave.preUpdate();


		// 提交流程任务
		Map<String, Object> vars = Maps.newHashMap();

		// 审核环节
		if ("audit".equals(taskDefKey)){

		} else if ("deptLeaderAudit".equals(taskDefKey)){
			leave.setLeadText(leave.getAct().getComment());
			dao.updateLeadText(leave);
			vars.put("deptLeaderPass", "yes".equals(leave.getAct().getFlag())? true : false);

		} else if ("hrAudit".equals(taskDefKey)){
			leave.setHrText(leave.getAct().getComment());
			dao.updateHrText(leave);
			vars.put("hrPass", "yes".equals(leave.getAct().getFlag())? true : false);
		} else{
			//未知环节，直接返回
			return;
		}

		actTaskService.complete(leave.getAct().getTaskId(), leave.getAct().getProcInsId(), leave.getAct().getComment(), vars);
	}

	/**
	 * 查询待办任务
	 * @param userId 用户ID
	 * @return
	 */
	public List<Leave> findTodoTasks(String userId) {
		
		List<Leave> results = new ArrayList<Leave>();
		List<Task> tasks = new ArrayList<Task>();
		// 根据当前人的ID查询
		List<Task> todoList = taskService.createTaskQuery().processDefinitionKey(ActUtils.PD_LEAVE[0]).taskAssignee(userId).active().orderByTaskPriority().desc().orderByTaskCreateTime().desc().list();
		// 根据当前人未签收的任务
		List<Task> unsignedTasks = taskService.createTaskQuery().processDefinitionKey(ActUtils.PD_LEAVE[0]).taskCandidateUser(userId).active().orderByTaskPriority().desc().orderByTaskCreateTime().desc().list();
		// 合并
		tasks.addAll(todoList);
		tasks.addAll(unsignedTasks);
		// 根据流程的业务ID查询实体并关联
		for (Task task : tasks) {
			String processInstanceId = task.getProcessInstanceId();
			ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).active().singleResult();
			String businessKey = processInstance.getBusinessKey();
			Leave leave = leaveDao.get(businessKey);
			leave.setTask(task);
			leave.setProcessInstance(processInstance);
			leave.setProcessDefinition(repositoryService.createProcessDefinitionQuery().processDefinitionId((processInstance.getProcessDefinitionId())).singleResult());
			results.add(leave);
		}
		return results;
	}

	public Page<Leave> find(Page<Leave> page, Leave leave) {

		leave.getSqlMap().put("dsf", dataScopeFilter(leave.getCurrentUser(), "o", "u"));
		
		leave.setPage(page);
		page.setList(leaveDao.findList(leave));
		
		for(Leave item : page.getList()) {
			String processInstanceId = item.getProcessInstanceId();
			Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).active().singleResult();
			item.setTask(task);
			HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
			if(historicProcessInstance!=null) {
				item.setHistoricProcessInstance(historicProcessInstance);
				item.setProcessDefinition(repositoryService.createProcessDefinitionQuery().processDefinitionId(historicProcessInstance.getProcessDefinitionId()).singleResult());
			} else {
				ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).active().singleResult();
				if (processInstance != null){
					item.setProcessInstance(processInstance);
					item.setProcessDefinition(repositoryService.createProcessDefinitionQuery().processDefinitionId(processInstance.getProcessDefinitionId()).singleResult());
				}
			}
		}
		return page;
	}
}
