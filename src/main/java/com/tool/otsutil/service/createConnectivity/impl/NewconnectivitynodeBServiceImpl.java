package com.tool.otsutil.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tool.otsutil.mapper.NewconnectivitynodeBMapper;
import com.tool.otsutil.model.entity.NewConnectivitynodeB;
import com.tool.otsutil.service.tmp.NewconnectivitynodeBService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NewconnectivitynodeBServiceImpl extends ServiceImpl<NewconnectivitynodeBMapper, NewConnectivitynodeB> implements NewconnectivitynodeBService {

	@Autowired
	private NewconnectivitynodeBMapper newconnectivitynodeBMapper;

	@Override
	public boolean insertNewConnectivitynodeB(NewConnectivitynodeB newConnectivitynodeB) {

		// Insert the newConnectivitynodeB entity into the database
		newconnectivitynodeBMapper.insert(newConnectivitynodeB);

		this.saveOrUpdate(newConnectivitynodeB);
		return true;
	}
}
