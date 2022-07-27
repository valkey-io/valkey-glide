class CoreCommands:
    def get(self, key, *args, **kwargs):
        return self.execute_command("get", key, *args, **kwargs)

    def set(self, key, value, *args, **kwargs):
        return self.execute_command("set", key, value, *args, **kwargs)

    def get_direct(self, key):
        return self.connection.get(key)

    def set_direct(self, key, value):
        return self.connection.set(key, value)
