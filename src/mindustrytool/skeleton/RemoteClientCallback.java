package mindustrytool.skeleton;

import java.rmi.Remote;
import java.rmi.RemoteException;

import mindustrytool.messages.request.GetServersMessageRequest;
import mindustrytool.messages.request.PlayerMessageRequest;
import mindustrytool.messages.request.SetPlayerMessageRequest;
import mindustrytool.messages.response.GetServersMessageResponse;

public interface RemoteClientCallback extends Remote{
    SetPlayerMessageRequest onLogin(PlayerMessageRequest request) throws RemoteException;

    int onTotalPlayer() throws RemoteException;

    void onChatMessage(String chat) throws RemoteException;

    void onPlayerLeave(PlayerMessageRequest request) throws RemoteException;

    GetServersMessageResponse onGetServer(GetServersMessageRequest request) throws RemoteException;

    int onStartServer(String serverId) throws RemoteException;
}
