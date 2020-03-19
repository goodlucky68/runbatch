package com.cmi.business;
import com.cmi.Modle.ApiAction;
import com.cmi.Modle.ErrorHandleAction;
import com.cmi.Modle.PreProcessAction;
import com.cmi.listObject.MagListBean;
import com.cmi.log.LogWriter;
import com.cmi.tools.DBUtil;
import com.cmi.tools.DataParse;
import com.cmi.tools.DateUtil;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Parameters;
import org.testng.annotations.Optional;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

public class RunBatch {

    @Parameters({"address","runTimes","dbAddress"})
    @Test
    public void excute(@Optional("127.0.0.1:8001") String address, @Optional("1") String runTimes,@Optional("127.0.0.1.443") String dbAddress) throws Exception{
        LogWriter.info(RunBatch.class,"前端地址："+address+" 跑批次数："+runTimes+" 数据库地址："+dbAddress);
        Reporter.log("前端地址："+address);
        Reporter.log("跑批次数："+runTimes);
        Reporter.log("数据库地址："+dbAddress);
        Boolean flag=true;  // 运行标志位
        String runStatus="";
        int runtime =Integer.parseInt(runTimes);   // 执行次数
        for(int i=1;i<=runtime;i++){

            LogWriter.info(RunBatch.class,"---------------------------------执行第"+i+"次跑批-----------------------------------");


            Reporter.log("---------------------------------执行第"+i+"次跑批-----------------------------------");
            Reporter.log("----执行时间"+DateUtil.getTime()+"----");
            // 调用预处理功能
            Reporter.log("***调用预处理功能***");
            Assert.assertTrue(new PreProcessAction().preProcess(dbAddress));
           Assert.assertTrue(new PreProcessAction().changePwd(DateUtil.getDate(),dbAddress)); // 修改密码更改时间
            Reporter.log("***调用预处理功能结束***");
             ApiAction apiAction = new ApiAction(address);


             String session="";
              String getSession=  apiAction.login(DataParse.GetProperties("USER"),DataParse.GetProperties("PASSWORD1"));
             // 登陆失败
            Reporter.log("***调用接口获取session***");
              if (getSession==null|getSession.equals("fail")){

              }else{
                  // 登陆成功
                  session=getSession;
              }
            Reporter.log("***获取批次运行状态***");
               runStatus =apiAction.checkRunStatus(session); // 获取运行状态

               if(runStatus.equals("未运行")){
                   Reporter.log("***批次状态未运行***");
                   Reporter.log("***校验批次任务生成状态***");
                   String checkSBatchCreate =apiAction.checkBatchCreated(session);
                   if(checkSBatchCreate.equals("Y")){   // 验证批次生成 未生成任务      // 生成任务状态为created
                       Reporter.log("***批次任务未生成，生成批次***");
                       String createBatch=apiAction.createBatch(session); // 生成批次
                       if (!createBatch.equals("true")){   // 验证批次生成是否成功
                           flag=false;
                           Reporter.log("***批次生成失败***");
                           LogWriter.error(RunBatch.class,"批次生成失败");
                           Assert.assertTrue(flag);

                       }
                   }

               }else if(runStatus.equals("正在运行")){
                   flag=false;
                   LogWriter.error(RunBatch.class,"仍有批次正在运行，请检查批次任务稍后再试");
                   Reporter.log("***有其他批次运行 稍后再试***");
                   Assert.assertTrue(flag);
               }

               String rundate= new ApiAction(address).magList(session).get(1).getPrcs_dt();
               LogWriter.info(RunBatch.class,"执行批次日期:【"+rundate+"】");
               Reporter.log("执行批次日期:【"+rundate+"】");

              //执行跑批任务
            try {
                Reporter.log("***执行批次任务***");
                String runBatch = apiAction.runBatch(session);

                if(runBatch.equals("true")){  // 跑批成功
                    Reporter.log("***跑批成功***");
                    LogWriter.info(RunBatch.class,"跑批成功");
                    //获取前端处理结果
                }else if(runBatch.equals("false")){  // 跑批失败


                    Boolean errorHandFlag;
                    String rerun="";

                    Boolean rerunBatch=ErrorHand(runBatch.equals("true"),session,address,dbAddress);


                    if(rerunBatch==false){
                        Reporter.log("***再次跑批仍失败，执行跑批日期：["+rundate+"]***");
                        Reporter.log("执行批次"+i+"日失败,已执行成功"+(i-1)+"次");
                        Reporter.log("---------------------------------结束第"+i+"次跑批-----------------------------------");
                        Reporter.log("----执行时间"+DateUtil.getTime()+"----");

                        LogWriter.error(RunBatch.class,"跑批失败请检查跑批结果或日志");
                        LogWriter.info(RunBatch.class,"执行批次日期"+rundate);
                        LogWriter.info(RunBatch.class,"执行批次"+i+"日失败,已执行成功"+(i-1)+"次");
                        LogWriter.info(RunBatch.class,"---------------------------------结束第"+i+"次跑批-----------------------------------");
                        Assert.assertTrue(false);
                    }
                    if(rerunBatch==true){
                        Reporter.log("***重新跑批成功***");
                    }
                    Assert.assertTrue(rerunBatch,"重跑跑失败");


                }



            }catch (Exception e){
                Reporter.log("启动跑批失败 请检查原因");
                LogWriter.error(RunBatch.class,"启动跑批失败 请检查原因");
                e.printStackTrace();
                Assert.assertTrue(false);
            }
               // 监控结果
            Reporter.log("----执行结束时间"+DateUtil.getTime()+"----");
            Reporter.log("---------------------------------结束第"+i+"次跑批-----------------------------------");
            LogWriter.info(RunBatch.class,"---------------------------------结束第"+i+"次跑批-----------------------------------");
            if((runtime-i)!=0){
                Reporter.log("等待12Min 准备执行下一批次....");
                LogWriter.info(RunBatch.class,"等待12Min 准备执行下一批次....");
                Thread.sleep(1000*60*15);  // 跑批成功 等待12分钟  执行下一批次
            }

        }

    }
    public Boolean ErrorHand(Boolean runResult,String session,String address,String dbAddress) throws IOException{
        Boolean flag;
        String errorNo="";
        String logNo="";
        Boolean runResults=false;
        if (runResult==false){
            ErrorHandleAction errorHandle = new ErrorHandleAction(address);
             errorNo = errorHandle.getMagErrorProcess(session);  // 获取失败任务序号
            if(errorNo.equals("110")){

                 logNo=   new  ErrorHandleAction(address).getLogErrorProcess(session);
            }

            LogWriter.error(RunBatch.class,"跑批失败 出错任务号："+errorNo+"  出错处理中...");
           Boolean errorHandFlag= errorHandle.fixMagError(errorNo,session,dbAddress);  // 处理失败任务
            if (errorHandFlag==false){  // 不能处理得任务 没有匹配项 结束跑批
                runResults=false;
            }else if(errorHandFlag==true){  //有匹配项错误处理 重跑
                Reporter.log("错误处理结束,重新跑批");
                flag=new ApiAction(address).runBatch(session).equals("true");
                if(flag==false){
                    Reporter.log("***重新跑批失败***");
                    ErrorHandleAction errorHandle1 = new ErrorHandleAction(address);
                  String  errorNo1 = errorHandle1.getMagErrorProcess(session);  // 获取失败任务序号
                if(errorNo1.equals("110")){
                    String logNo1 =   new  ErrorHandleAction(address).getLogErrorProcess(session);
                 if (logNo1.equals(logNo)){

                     runResults=false;
                     Reporter.log("错误任务处理仍失败 请检查核算日志 错误任务号："+logNo);
                 }
                }else{
                    return ErrorHand(flag,session,address,dbAddress);

                }

                }else if(runResult==true){
                    Reporter.log("***重新跑批成功***");
                    runResults=true;
                }
            }
        }
        return runResults;


    }

}
