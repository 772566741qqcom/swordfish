/*
 * Copyright (C) 2017 Baifendian Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.baifendian.swordfish.execserver.runner.node;

import com.baifendian.swordfish.common.config.BaseConfig;
import com.baifendian.swordfish.common.utils.http.HttpUtil;
import com.baifendian.swordfish.dao.model.ExecutionFlow;
import com.baifendian.swordfish.dao.model.ExecutionNode;
import com.baifendian.swordfish.dao.model.FlowNode;
import com.baifendian.swordfish.execserver.job.Job;
import com.baifendian.swordfish.execserver.job.JobContext;
import com.baifendian.swordfish.execserver.job.JobManager;
import com.baifendian.swordfish.execserver.job.JobProps;
import com.baifendian.swordfish.execserver.utils.JobLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

/**
 * 节点执行器 <p>
 */
public class NodeRunner implements Callable<Boolean> {

  /**
   * logger
   */
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ExecutionFlow executionFlow;

  private final ExecutionNode executionNode;

  private final FlowNode flowNode;

  private final Map<String, String> systemParamMap;

  private final Map<String, String> customParamMap;

  private Job job;

  private Semaphore semaphore;

  public NodeRunner(JobContext jobContext) {
    this.executionFlow = jobContext.getExecutionFlow();
    this.executionNode = jobContext.getExecutionNode();
    this.flowNode = jobContext.getFlowNode();
    this.systemParamMap = jobContext.getSystemParamMap();
    this.customParamMap = jobContext.getCustomParamMap();
    this.semaphore = jobContext.getSemaphore();
  }

  /**
   * 得到执行的结点
   *
   * @return
   */
  public ExecutionNode getExecutionNode() {
    return executionNode;
  }

  /**
   * 返回结点名称
   *
   * @return
   */
  public String getNodename() {
    return flowNode.getName();
  }

  @Override
  public Boolean call() {
    // "项目id/flowId/执行id"
    String jobScriptPath = BaseConfig.getFlowExecDir(executionFlow.getProjectId(), executionFlow.getFlowId(), executionFlow.getId());

    logger.info("exec id:{}, node:{}, script path:{}", executionFlow.getId(), executionNode.getName(), jobScriptPath);

    // 作业参数配置
    Map<String, String> allParamMap = new HashMap<>();

    if (systemParamMap != null) {
      allParamMap.putAll(systemParamMap);
    }

    if (customParamMap != null) {
      allParamMap.putAll(customParamMap);
    }

    JobProps props = new JobProps();

    props.setJobParams(flowNode.getParameter());
    props.setWorkDir(jobScriptPath);
    props.setProxyUser(executionFlow.getProxyUser());
    props.setDefinedParams(allParamMap);
    props.setProjectId(executionFlow.getProjectId());
    props.setWorkflowId(executionFlow.getFlowId());
    props.setNodeName(flowNode.getName());
    props.setExecId(executionFlow.getId());
    props.setEnvFile(BaseConfig.getSystemEnvPath());
    props.setQueue(executionFlow.getQueue());
    props.setFlowStartTime(executionFlow.getStartTime());
    props.setFlowTimeout(executionFlow.getTimeout());

    props.setJobAppId(String.format("%s_%s", executionNode.getJobId(), HttpUtil.getMd5(executionNode.getName()).substring(0, 8)));

    JobLogger jobLogger = new JobLogger(executionNode.getJobId(), logger);

    boolean success = false;

    try {
      job = JobManager.newJob(flowNode.getType(), props, jobLogger);

      // job 的前处理
      job.before();

      // job 的处理过程
      job.process();

      // job 的后处理过程
      job.after();

      success = (job.getExitCode() == 0);
    } catch (Exception e) {
      success = false;

      logger.error(String.format("job process exception, exec id: {}, node: {}", executionFlow.getId(), executionNode.getName()), e);

      kill();
    } finally {
      semaphore.release();

      logger.info("job process done, exec id: {}, node: {}, success: {}", executionFlow.getId(), executionNode.getName(), success);
    }

    return success;
  }

  /**
   * 关闭任务
   */
  public void kill() {
    if (job != null && job.isStarted()) {
      try {
        job.cancel();
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    }
  }
}
