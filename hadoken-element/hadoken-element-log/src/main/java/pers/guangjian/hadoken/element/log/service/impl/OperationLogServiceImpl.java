package pers.guangjian.hadoken.element.log.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import pers.guangjian.hadoken.common.util.page.PageUtil;
import pers.guangjian.hadoken.common.util.validation.ValidationUtil;
import pers.guangjian.hadoken.element.log.annotation.Log;
import pers.guangjian.hadoken.element.log.domain.po.OperationLog;
import pers.guangjian.hadoken.element.log.domain.query.OperationLogQueryCriteria;
import pers.guangjian.hadoken.element.log.repository.OperationLogRepository;
import pers.guangjian.hadoken.element.log.service.OperationLogService;
import pers.guangjian.hadoken.element.log.service.mapstruct.OperationLogErrorMapper;
import pers.guangjian.hadoken.element.log.service.mapstruct.OperationLogSmallMapper;
import pers.guangjian.hadoken.jpa.QueryHelp;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * @author yanggj
 * @version 1.0.0
 * @date 2022/3/12 23:13
 */
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogRepository logRepository;
    private final OperationLogErrorMapper logErrorMapper;
    private final OperationLogSmallMapper logSmallMapper;

    @Override
    public Object queryAll(OperationLogQueryCriteria criteria, Pageable pageable) {
        Page<OperationLog> page = logRepository.findAll(((root, criteriaQuery, cb) -> QueryHelp.getPredicate(root, criteria, cb)), pageable);
        String status = "ERROR";
        if (status.equals(criteria.getLogType())) {
            return PageUtil.toPage(page.map(logErrorMapper::toDto));
        }
        return page;
    }

    @Override
    public List<OperationLog> queryAll(OperationLogQueryCriteria criteria) {
        return logRepository.findAll(((root, criteriaQuery, cb) -> QueryHelp.getPredicate(root, criteria, cb)));
    }

    @Override
    public Object queryAllByUser(OperationLogQueryCriteria criteria, Pageable pageable) {
        Page<OperationLog> page = logRepository.findAll(((root, criteriaQuery, cb) -> QueryHelp.getPredicate(root, criteria, cb)), pageable);
        return PageUtil.toPage(page.map(logSmallMapper::toDto));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(String username, String browser, String ip, ProceedingJoinPoint joinPoint, OperationLog log) {
        if (log == null) {
            throw new IllegalArgumentException("Log ????????? null!");
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log aopLog = method.getAnnotation(Log.class);

        // ????????????
        String methodName = joinPoint.getTarget().getClass().getName() + "." + signature.getName() + "()";

        // ??????
        log.setDescription(aopLog.value());

        log.setRequestIp(ip);
        //log.setAddress(StringUtils.getCityInfo(log.getRequestIp()));
        log.setMethod(methodName);
        log.setUsername(username);
        log.setParams(getParameter(method, joinPoint.getArgs()));
        log.setBrowser(browser);
        logRepository.save(log);
    }

    /**
     * ????????????????????????????????????????????????
     */
    private String getParameter(Method method, Object[] args) {
        List<Object> argList = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            //???RequestBody???????????????????????????????????????
            RequestBody requestBody = parameters[i].getAnnotation(RequestBody.class);
            if (requestBody != null) {
                argList.add(args[i]);
            }
            //???RequestParam???????????????????????????????????????
            RequestParam requestParam = parameters[i].getAnnotation(RequestParam.class);
            if (requestParam != null) {
                Map<String, Object> map = new HashMap<>(4);
                String key = parameters[i].getName();
                if (!StrUtil.isEmpty(requestParam.value())) {
                    key = requestParam.value();
                }
                map.put(key, args[i]);
                argList.add(map);
            }
        }
        if (argList.isEmpty()) {
            return "";
        }
        return argList.size() == 1 ? JSONUtil.toJsonStr(argList.get(0)) : JSONUtil.toJsonStr(argList);
    }

    @Override
    public Object findByErrDetail(Long id) {
        OperationLog log = logRepository.findById(id).orElseGet(OperationLog::new);
        ValidationUtil.isNull(log.getId(), "Log", "id", id);
        byte[] details = log.getExceptionDetail();
        return Dict.create().set("exception", new String(ObjectUtil.isNotNull(details) ? details : "".getBytes()));
    }

    @Override
    public void download(List<OperationLog> logs, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (OperationLog log : logs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("?????????", log.getUsername());
            map.put("IP", log.getRequestIp());
            map.put("IP??????", log.getAddress());
            map.put("??????", log.getDescription());
            map.put("?????????", log.getBrowser());
            map.put("????????????/??????", log.getDuration());
            map.put("????????????", new String(ObjectUtil.isNotNull(log.getExceptionDetail()) ? log.getExceptionDetail() : "".getBytes()));
            map.put("????????????", log.getCreateTime());
            list.add(map);
        }
        //FileUtil.downloadExcel(list, response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delAllByError() {
        logRepository.deleteByLogType("ERROR");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delAllByInfo() {
        logRepository.deleteByLogType("INFO");
    }
}
