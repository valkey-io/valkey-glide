class CoreCommands:
    def get(self, key, *args, **kwargs):
        return self.execute_command("GET", key, *args, **kwargs)

    def set(self, key, value, *args, **kwargs):
        return self.execute_command("SET", key, value, *args, **kwargs)
