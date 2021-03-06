package com.cqupt.mis.rms.filter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.cqupt.mis.rms.model.RolePurviewDyn;
import com.cqupt.mis.rms.service.impl.AuthorityResource;
import com.cqupt.mis.rms.utils.ResearchConstant;

/**
 * 进行权限验证的拦截器
 * @author Bern
 *
 */
public class AuthorityFilter implements Filter {
	
	private AuthorityResource authorityResource;
	
	private WebApplicationContext wac;
	
	private Map<String, List<Integer>> fixedResourceMap;	//资源URL为key， 对应的角色id为value
	
	private Map<Integer, Map<Integer, RolePurviewDyn>> dynamicResourceMap;	//外层map中：类别id为key， 权限为value；内层map中：角色id为key，权限为value

	private Set<String> noInterceptUrl;		//不进行拦截的url列表
	/**
	 * 初始化的方法
	 */
	public void init(FilterConfig config) throws ServletException {
		wac = WebApplicationContextUtils.getWebApplicationContext(config.getServletContext());
		authorityResource = (AuthorityResource) wac.getBean("authorityResource");
		
		fixedResourceMap = authorityResource.getFixedResourceMap();
		dynamicResourceMap = authorityResource.getDynamicResourceMap();
		
		noInterceptUrl = new HashSet<String>();
		noInterceptUrl.add("login");
		noInterceptUrl.add("error");
		noInterceptUrl.add("");
	}
	
	/**
	 * 销毁方法
	 */
	public void destroy() {
		// TODO Auto-generated method stub
		
	}

	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
			FilterChain chain) throws IOException, ServletException {
		
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		String requestUrl = request.getRequestURI();
		int index = requestUrl.indexOf("rms2/");
		requestUrl = requestUrl.substring(index+5);
		System.out.println(requestUrl);
		
		if(noIntercept(requestUrl)) {	//不拦截的页面，通过
			chain.doFilter(servletRequest, servletResponse);
			return;
		}
		
		if(decideFixed(request)) {	//静态权限匹配，通过
			chain.doFilter(servletRequest, servletResponse);
			return;
		}
		
		if(decideDynamic(request)) {	//动态权限匹配，通过
			chain.doFilter(servletRequest, servletResponse);
			return;
		}
		
		//权限校验不通过,重定向到越权界面
		response.sendRedirect("accessDenied.jsp");
		
	}
	
	/**
	 * 检测是否满足静态资源的权限
	 * @param request
	 * @return
	 */
	public boolean decideFixed(HttpServletRequest request) {
		String requestUrl = request.getRequestURI();
		
		if(fixedResourceMap.containsKey(requestUrl)) {
			int roleId = (Integer) request.getSession().getAttribute("roleId");
			List<Integer> list = fixedResourceMap.get(requestUrl);
			if(list.contains(roleId)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * 检测是否为不进行拦截的URL
	 * @param url
	 * @return
	 */
	public boolean noIntercept(String url) {
		if(noInterceptUrl.contains(url))
			return true;
		
		return false;
			
	}
	
	/**
	 * 检测是否满足动态资源的权限
	 */
	public boolean decideDynamic(HttpServletRequest request) {
		String requestUrl = request.getRequestURI();
		int roleId = (Integer) request.getSession().getAttribute("roleId");		//角色Id
		
		//获取不带参数的url、参数"classId"的值
		int index1 = requestUrl.indexOf("?");
		int index2 = index1 + ResearchConstant.PARAM1.length();
		String urlNoParam = requestUrl.substring(0, index1);		//请求URL路径不包括参数列表
		String param1Value = requestUrl.substring(index2, index2+1);		//参数"classId"的value
		
		int classId = 0;
		try {		
			 classId = Integer.parseInt(param1Value);
		} catch(NumberFormatException e) {		//classId参数值转换失败，说明参数中不包含classId，判定为不是动态权限，拒绝访问
			return false;
		}
		
		//获取相应科研类别的权限列表
		Map<Integer, RolePurviewDyn> map = dynamicResourceMap.get(classId);
		if(map == null) {
			return false;
		}
		RolePurviewDyn rolePurviewDyn = map.get(roleId);
		if(rolePurviewDyn == null) {	//该角色没有当前科研类别的任何权限，拒绝访问
			return false;
		}
		
		if(ResearchConstant.INPUTURL.equals(urlNoParam)) {	//验证是否有<录入科研记录>的权限
			return rolePurviewDyn.isInput();
		} else if(ResearchConstant.MANAGEURL.equals(urlNoParam)) {	//验证是否有<管理个人科研记录>的权限
			return rolePurviewDyn.isManage();
		} else if(ResearchConstant.APPROVEURL.equals(urlNoParam)) {	//验证是否有<审批科研记录>的权限
			return rolePurviewDyn.isApprove();
		} else if(ResearchConstant.STATISTICSURL.equals(urlNoParam)) {	//验证是否有<查询统计科研记录>的权限
			return rolePurviewDyn.isStatistics();
		}
		
		return false;
	}
	
}
