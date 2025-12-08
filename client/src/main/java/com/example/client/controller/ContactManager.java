package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;

import java.util.List;
import java.util.Map;

public class ContactManager {
    private final MainController mc;

    public ContactManager(MainController mc) {
        this.mc = mc;
    }

    public void loadFriendListInitial() {
        new Thread(() -> {
            try {
                List<UserDTO> friends = RmiClient.getFriendService().getFriendList(SessionStore.currentUser.getId());
                Map<Long, Integer> unreadMap = RmiClient.getMessageService().getUnreadCounts(SessionStore.currentUser.getId());
                for (UserDTO u : friends) {
                    if (unreadMap.containsKey(u.getId())) u.setUnreadCount(unreadMap.get(u.getId()));
                }
                Platform.runLater(() -> {
                    mc.conversationList.getItems().clear();
                    mc.conversationList.getItems().addAll(friends);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    public void updateFriendInList(UserDTO updatedFriend) {
        boolean found = false;
        for (int i = 0; i < mc.conversationList.getItems().size(); i++) {
            UserDTO u = mc.conversationList.getItems().get(i);
            if (u.getId() == updatedFriend.getId()) {
                u.setOnline(updatedFriend.isOnline());
                u.setLastIp(updatedFriend.getLastIp());
                u.setLastPort(updatedFriend.getLastPort());
                if (updatedFriend.getAvatarUrl() != null) u.setAvatarUrl(updatedFriend.getAvatarUrl());
                if (updatedFriend.getStatusMsg() != null) u.setStatusMsg(updatedFriend.getStatusMsg());
                found = true;
                mc.conversationList.getItems().set(i, u);
                if (mc.currentChatUser != null && mc.currentChatUser.getId() == updatedFriend.getId()) {
                    mc.currentChatUser = u;
                }
                break;
            }
        }
        if (!found) mc.conversationList.getItems().add(0, updatedFriend);
        mc.conversationList.refresh();
    }

    public void addFriendToListDirectly(UserDTO newFriend) {
        Platform.runLater(() -> updateFriendInList(newFriend));
    }

    public void moveUserToTop(MessageDTO msg) {
        Platform.runLater(() -> {
            UserDTO targetUser = null;
            int index = -1;
            UserDTO currentSelection = mc.conversationList.getSelectionModel().getSelectedItem();

            for (int i = 0; i < mc.conversationList.getItems().size(); i++) {
                UserDTO u = mc.conversationList.getItems().get(i);
                boolean match = false;
                if ("GROUP".equals(u.getUsername())) {
                    if (u.getId() == msg.getConversationId()) match = true;
                } else {
                    if (u.getId() == msg.getSenderId()) match = true;
                    if (u.getId() == msg.getConversationId() && msg.getSenderId() == SessionStore.currentUser.getId()) match = true;
                }
                if (match) {
                    targetUser = u;
                    index = i;
                    break;
                }
            }

            if (targetUser != null && index != -1) {
                boolean isChattingWithThis = (mc.currentChatUser != null && mc.currentChatUser.getId() == targetUser.getId());
                if (!isChattingWithThis && msg.getSenderId() != SessionStore.currentUser.getId()) {
                    targetUser.setUnreadCount(targetUser.getUnreadCount() + 1);
                }

                mc.isUpdatingList = true;
                try {
                    mc.conversationList.getItems().remove(index);
                    mc.conversationList.getItems().add(0, targetUser);
                    if (currentSelection != null) {
                        mc.conversationList.getSelectionModel().select(currentSelection);
                    } else {
                        mc.conversationList.getSelectionModel().clearSelection();
                    }
                } finally {
                    mc.isUpdatingList = false;
                }
                mc.conversationList.refresh();
                mc.conversationList.scrollTo(0);
            }
        });
    }

    public UserDTO findUserInList(long userId) {
        for (UserDTO u : mc.conversationList.getItems()) {
            if (u.getId() == userId) return u;
        }
        return null;
    }
}