package com.tool.otsutil.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tool.otsutil.mapper.ConnectivityTmpMapper;
import com.tool.otsutil.model.entity.ConnectivityTmp;
import com.tool.otsutil.service.tmp.ConnectivityTmpService;
import org.springframework.stereotype.Service;

@Service
public class ConnectivityTmpServiceImpl extends ServiceImpl<ConnectivityTmpMapper, ConnectivityTmp> implements ConnectivityTmpService {
}
