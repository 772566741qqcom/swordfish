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
package com.baifendian.swordfish.execserver.job;

import com.baifendian.swordfish.common.hadoop.YarnRestClient;
import com.baifendian.swordfish.dao.DaoFactory;
import com.baifendian.swordfish.dao.FlowDao;
import com.baifendian.swordfish.dao.StreamingDao;
import com.baifendian.swordfish.dao.enums.FlowStatus;
import com.baifendian.swordfish.dao.model.ExecutionNode;
import com.baifendian.swordfish.dao.model.StreamingResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

public abstract class AbstractYarnJob extends Job {

  // 抽取应用 id 的规则
  private static final Pattern APPLICATION_REGEX = Pattern.compile("application_\\d+_\\d+");

  // 抽取 job id 的规则
  private static final Pattern JOB_REGEX = Pattern.compile("job_\\d+_\\d+");

  /**
   * 短任务数据库接口
   */
  private FlowDao flowDao;

  /**
   * 流任务数据库接口
   */
  private StreamingDao streamingDao;

  /**
   * 应用 links
   */
  protected List<String> appLinks;

  /**
   * 日志 links
   */
  protected List<String> jobLinks;

  public AbstractYarnJob(JobProps props, boolean isLongJob, Logger logger) {
    super(props, isLongJob, logger);

    flowDao = DaoFactory.getDaoInstance(FlowDao.class);
    streamingDao = DaoFactory.getDaoInstance(StreamingDao.class);

    appLinks = Collections.synchronizedList(new ArrayList<>());
    jobLinks = Collections.synchronizedList(new ArrayList<>());
  }

  /**
   * 处理 log 日志, 是批量日志
   *
   * @param logs 待打印日志
   */
  @Override
  public void logProcess(List<String> logs) {
    logger.info("(stdout, stderr) -> \n{}", String.join("\n", logs));

    boolean captureAppLinks = false;
    boolean captureJobLinks = false;

    // 分析日志
    for (String log : logs) {
      // app id 操作
      String appId = findAppId(log);

      if (StringUtils.isNotEmpty(appId) && !appLinks.contains(appId)) {
        appLinks.add(appId);
        captureAppLinks = true;
      }

      // job id 操作
      String jobId = findJobId(log);

      if (StringUtils.isNotEmpty(jobId) && !jobLinks.contains(jobId)) {
        jobLinks.add(jobId);
        captureJobLinks = true;
      }
    }

    // 有一个改变才能进行里面的操作
    if (captureAppLinks || captureJobLinks) {
      // 短任务
      if (!isLongJob()) {
        ExecutionNode executionNode = flowDao
            .queryExecutionNode(props.getExecId(), props.getNodeName());

        if (executionNode != null) {
          if (captureAppLinks) {
            executionNode.setAppLinkList(appLinks);
          }

          if (captureJobLinks) {
            executionNode.setJobLinkList(appLinks);
          }

          flowDao.updateExecutionNode(executionNode);
        }
      } else { // 长任务
        StreamingResult streamingResult = streamingDao.queryStreamingExec(props.getExecId());

        if (streamingResult != null) {
          if (captureAppLinks) {
            streamingResult.setAppLinkList(appLinks);
          }

          if (captureJobLinks) {
            streamingResult.setJobLinkList(appLinks);
          }

          streamingDao.updateResult(streamingResult);
        }
      }
    }

    // 如果已经被取消, 感觉取消应用, 不然会比较危险
    if (isCancel()) {
      try {
        cancel(true);
      } catch (Exception e) {
      }
    }
  }

  /**
   * 是否完成, 对于 yarn 应用来说, 这个运行没有运行完成, 根据
   */
  @Override
  public boolean isCompleted() {
    if (CollectionUtils.isNotEmpty(appLinks)) {
      String appId = appLinks.get(appLinks.size() - 1);

      try {
        FlowStatus status = YarnRestClient.getInstance().getApplicationStatus(appId);

        if (status == null) {
          complete = false;
          return complete;
        }

        logger.info("current status is: {}", status);

        // 如果是完成了或者是运行中, 我们认为是 OK 的
        if (status.typeIsFinished() || status == FlowStatus.RUNNING) {
          complete = true;
        }
      } catch (Exception e) {
        logger.error(String.format("request status of application %s exception", appId), e);
        complete = true;
      }
    }

    return complete;
  }

  /**
   * 取消 yarn 上的应用
   *
   * @param cancelApplication 是否取消相应的 yarn 应用
   */
  @Override
  public void cancel(boolean cancelApplication) throws Exception {
    logger.info("cancel yarn application");

    cancel = true;

    if (cancelApplication) {
      cancelApplication(appLinks, props, logger);
    }
  }

  /**
   * 关闭应用
   *
   * @param appLinks 应用列表
   * @param props 每个任务, 应该有一个唯一的 job application id, 这里用于生成脚本
   */
  public static void cancelApplication(List<String> appLinks, JobProps props, Logger logger)
      throws IOException {
    // 然后 kill application, 一般来说, 就是最后一个(前面的都运行完了)
    if (CollectionUtils.isNotEmpty(appLinks)) {
      String appid = appLinks.get(appLinks.size() - 1);
      String commandFile = String
          .format("%s/%s_%s.kill", props.getWorkDir(), props.getJobAppId(), appid);
      String cmd = "yarn application -kill " + appid;

      StringBuilder sb = new StringBuilder();
      sb.append("#!/bin/sh\n");
      sb.append("BASEDIR=$(cd `dirname $0`; pwd)\n");
      sb.append("cd $BASEDIR\n");

      if (props.getEnvFile() != null) {
        sb.append("source " + props.getEnvFile() + "\n");
      }

      sb.append("\n\n");
      sb.append(cmd);

      File f = new File(commandFile);

      if (!f.exists()) {
        FileUtils.writeStringToFile(new File(commandFile), sb.toString(), Charset.forName("UTF-8"));
      }

      // 以某账号运行
      String runCmd = "sh " + commandFile;
      if (StringUtils.isNotEmpty(props.getProxyUser())) {
        runCmd = "sudo -u " + props.getProxyUser() + " " + runCmd;
      }

      logger.info("kill cmd:{}", runCmd);

      try {
        // 一般来说, 这种命令挺消耗资源, 但是一般也很快
        Runtime.getRuntime().exec(runCmd);
      } catch (Exception e) {
        logger.error(String.format("kill application %s exception", appid), e);
      }
    }
  }

  /**
   * 获取 appid <p>
   *
   * @return appid
   */
  private String findAppId(String line) {
    Matcher matcher = APPLICATION_REGEX.matcher(line);

    if (matcher.find()) {
      return matcher.group();
    }

    return null;
  }

  /**
   * 查找 job id
   */
  private String findJobId(String line) {
    Matcher matcher = JOB_REGEX.matcher(line);

    if (matcher.find()) {
      return matcher.group();
    }

    return null;
  }

  public static void main(String[] args) {
    String msg =
        "[INFO] 2017-05-23 18:25:22.268 com.baifendian.swordfish.execserver.runner.node.NodeRunner:[147] -  hive execute log : INFO  : Starting Job = job_1493947416024_0139, Tracking URL = http://hlg-5p149-wangwenting:8088/proxy/application_1493947416024_0139/\n"
            +
            "job_1493947416024_0140 [INFO] 2017-05-23 18:25:22.268 com.baifendian.swordfish.execserver.runner.node.NodeRunner:[147] -  hive execute log : INFO  : Kill Command = /opt/hadoop/bin/hadoop job  -kill job_1493947416024_0139\n"
            +
            "[INFO] 2017-05-23 18:25:27.269 com.baifendian.swordfish.execserver.runner.node.NodeRunner:[147] -  hive execute log : INFO  : Hadoop job information for Stage-1: number of mappers: 1; number of reducers: 0";

    // 查找 application id
    Matcher matcher = APPLICATION_REGEX.matcher(msg);

    while (matcher.find()) {
      System.out.println(matcher.group());
    }

    // 查找 job id
    matcher = JOB_REGEX.matcher(msg);

    while (matcher.find()) {
      System.out.println(matcher.group());
    }

    // 测试另外的 msg
    msg = "sh.execserver.runner.node.NodeRunner:application[147] -  hive execute log : INFO  : Hadoop job information for Stage-1: number of mappers: 1; number of reducers: 0";

    // 查找 application id
    matcher = APPLICATION_REGEX.matcher(msg);

    while (matcher.find()) {
      System.out.println(matcher.group());
    }

    // 查找 job id
    matcher = JOB_REGEX.matcher(msg);

    while (matcher.find()) {
      System.out.println(matcher.group());
    }
  }
}
