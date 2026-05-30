export interface ConnectionRegistryPort {
  register(userId: string, socketId: string): Promise<void>;
  unregister(userId: string, socketId: string): Promise<void>;
  refreshRegistration(userId: string): Promise<void>;
  getSocketIds(userId: string): Promise<string[]>;
  isOnline(userId: string): Promise<boolean>;
  enqueueOffline(userId: string, notificationId: string): Promise<void>;
  drainOfflineQueue(userId: string): Promise<string[]>;
}

export const CONNECTION_REGISTRY_PORT = Symbol('CONNECTION_REGISTRY_PORT');
