<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="/WEB-INF/views/include/taglib.jsp"%>
<html>
<head>
	<title>请假管理</title>
	<meta name="decorator" content="default"/>
	<script type="text/javascript">
		$(document).ready(function() {
			$("#name").focus();
			$("#inputForm").validate({
				submitHandler: function(form){
					loading('正在提交，请稍等...');
					form.submit();
				},
				errorContainer: "#messageBox",
				errorPlacement: function(error, element) {
					$("#messageBox").text("输入有误，请先更正。");
					if (element.is(":checkbox")||element.is(":radio")||element.parent().is(".input-append")){
						error.appendTo(element.parent().parent());
					} else {
						error.insertAfter(element);
					}
				}
			});
		});
	</script>
</head>
<body>
	<ul class="nav nav-tabs">
		<li><a href="${ctx}/oa/leave/">审批列表</a></li>
		<li class="active"><a href="#"><shiro:hasPermission name="oa:leave:edit">${leave.act.taskName}</shiro:hasPermission><shiro:lacksPermission name="oa:leave:edit">查看</shiro:lacksPermission></a></li>
	</ul>
	<form:form id="inputForm" modelAttribute="leave" action="${ctx}/oa/leave/saveAudit" method="post" class="form-horizontal">
		<form:hidden path="id"/>
		<form:hidden path="act.taskId"/>
		<form:hidden path="act.taskName"/>
		<form:hidden path="act.taskDefKey"/>
		<form:hidden path="act.procInsId"/>
		<form:hidden path="act.procDefId"/>
		<form:hidden id="flag" path="act.flag"/>
		<sys:message content="${message}"/>
		<div class="control-group">
			<label class="control-label">请假类型：</label>
			<div class="controls">
					${leave.leaveType}
			</div>
		</div>
		<div class="control-group">
			<label class="control-label">开始时间：</label>
			<div class="controls">
					${leave.startTime}
			</div>
		</div>
		<div class="control-group">
			<label class="control-label">结束时间：</label>
			<div class="controls">
				${leave.endTime}
			</div>
		</div>
		<div class="control-group">
			<label class="control-label">请假原因：</label>
			<div class="controls">
				${leave.reason}
			</div>
		</div>
		<c:if test="${not empty leave.leadText}">
			<div class="control-group">
				<label class="control-label">部门领导意见：</label>
				<div class="controls">
						${leave.leadText}
				</div>
			</div>
		</c:if>
		<c:if test="${not empty leave.hrText}">
			<div class="control-group">
				<label class="control-label">Hr 意见：</label>
				<div class="controls">
						${leave.hrText}
				</div>
			</div>
		</c:if>
		<c:if test="${leave.act.taskDefKey ne 'reportBack'}">
			<div class="control-group">
				<label class="control-label">您的意见：</label>
				<div class="controls">
					<form:textarea path="act.comment" class="required" rows="5" maxlength="20" cssStyle="width:500px"/>
				</div>
			</div>
		</c:if>

		<div class="form-actions">
			<shiro:hasPermission name="oa:testAudit:edit">
				<c:if test="${leave.act.taskDefKey ne 'reportBack'}">
					<input id="btnSubmit" class="btn btn-primary" type="submit" value="同 意" onclick="$('#flag').val('yes')"/>&nbsp;
					<input id="btnSubmit" class="btn btn-inverse" type="submit" value="驳 回" onclick="$('#flag').val('no')"/>&nbsp;
				</c:if>
			</shiro:hasPermission>
			<input id="btnCancel" class="btn" type="button" value="返 回" onclick="history.go(-1)"/>
		</div>
	</form:form>
</body>
</html>
