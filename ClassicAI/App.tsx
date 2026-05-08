import 'react-native-gesture-handler';
import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer } from '@react-navigation/native';
import { createDrawerNavigator } from '@react-navigation/drawer';
import { createStackNavigator } from '@react-navigation/stack';
import { AppProvider } from './src/providers/AppProvider';
import { ToastProvider } from './src/components/Toast';
import { SidebarDrawer } from './src/screens/SidebarDrawer';
import { ChatScreen } from './src/screens/ChatScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { ApiKeysScreen } from './src/screens/ApiKeysScreen';
import { CreateSpaceModal } from './src/screens/CreateSpaceModal';
import { SpaceListScreen } from './src/screens/SpaceListScreen';
import { SpaceChatScreen } from './src/screens/SpaceChatScreen';
import { SpaceDetailsScreen } from './src/screens/SpaceDetailsScreen';
import { Colors } from './src/constants/theme';

const Drawer = createDrawerNavigator();
const Stack = createStackNavigator();

function DrawerNavigator() {
  return (
    <Drawer.Navigator
      drawerContent={(props) => <SidebarDrawer {...props} />}
      screenOptions={{
        headerShown: false,
        drawerStyle: {
          width: 300,
          backgroundColor: Colors.sidebar,
        },
        drawerType: 'slide',
        overlayColor: 'rgba(0,0,0,0.5)',
      }}
    >
      <Drawer.Screen name="Chat" component={ChatScreen} />
    </Drawer.Navigator>
  );
}

function RootNavigator() {
  return (
    <Stack.Navigator
      screenOptions={{
        headerShown: false,
        cardStyle: { backgroundColor: Colors.background },
        presentation: 'card',
      }}
    >
      <Stack.Screen name="Main" component={DrawerNavigator} />
      <Stack.Screen name="Settings" component={SettingsScreen} />
      <Stack.Screen name="ApiKeys" component={ApiKeysScreen} />
      <Stack.Screen name="SpaceList" component={SpaceListScreen} />
      <Stack.Screen
        name="CreateSpace"
        component={CreateSpaceModal}
        options={{ presentation: 'modal' }}
      />
      <Stack.Screen name="SpaceChat" component={SpaceChatScreen} />
      <Stack.Screen name="SpaceDetails" component={SpaceDetailsScreen} />
    </Stack.Navigator>
  );
}

export default function App() {
  return (
    <AppProvider>
      <ToastProvider>
        <NavigationContainer
          theme={{
            dark: true,
            colors: {
              primary: Colors.accent,
              background: Colors.background,
              card: Colors.card,
              text: Colors.text,
              border: Colors.border,
              notification: Colors.accent,
            },
          }}
        >
          <StatusBar style="light" />
          <RootNavigator />
        </NavigationContainer>
      </ToastProvider>
    </AppProvider>
  );
}
