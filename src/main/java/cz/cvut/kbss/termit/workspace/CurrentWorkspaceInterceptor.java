package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.net.URI;

public class CurrentWorkspaceInterceptor implements HandlerInterceptor {

    @Autowired
    private WorkspaceStore workspaceStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        final HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(Constants.WORKSPACE_SESSION_ATT) != null) {
            workspaceStore.setCurrentWorkspace((URI) session.getAttribute(Constants.WORKSPACE_SESSION_ATT));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        workspaceStore.clear();
    }
}
