export function guardDatabase(host, port, name) {
  if (host !== '127.0.0.1' || Number(port) !== 13306 || !/^rbac1_contract_(structured|ddd)_\d{13}_[a-f0-9]{8}$/.test(name)) throw new Error('Unsafe database target refused');
  return '`' + name + '`';
}
