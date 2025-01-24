package mindustrytool.skeleton;

import java.rmi.Remote;

import mindustrytool.messages.request.GetServersMessageRequest;
import mindustrytool.messages.request.PlayerMessageRequest;
import mindustrytool.messages.request.SetPlayerMessageRequest;
import mindustrytool.messages.response.GetServersMessageResponse;

public interface ServerGateway extends Remote {
    public SetPlayerMessageRequest onLogin(PlayerMessageRequest request);

    public int onTotalPlayer();

    public void onChatMessage(String chat);

    public void onPlayerLeave(PlayerMessageRequest request);

    public int onStartServer(String serverId);

    public GetServersMessageResponse onGetServer(GetServersMessageRequest request);
}
