import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import { Shield, ShieldOff, User as UserIcon } from 'lucide-react';

interface User {
  id: string;
  email: string;
  name: string | null;
  role: string;
  createdAt: string;
}

export default function AdminUsers() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchUsers = async () => {
    try {
      const res = await fetch('/api/admin/users');
      if (res.ok) {
        const data = await res.json();
        setUsers(data);
      }
    } catch (error) {
      console.error('Failed to fetch users', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleRoleChange = async (userId: string, newRole: string) => {
    try {
      const res = await fetch(`/api/admin/users/${userId}/role`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ role: newRole })
      });

      if (res.ok) {
        toast.success(`User role updated to ${newRole}`, {
          style: { background: '#1a1500', color: '#ffd700', border: '1px solid rgba(184,134,11,0.3)' }
        });
        fetchUsers();
      } else {
        const data = await res.json();
        toast.error(data.error || 'Failed to update role');
      }
    } catch (error) {
      toast.error('An error occurred');
    }
  };

  if (loading) return <div className="text-gold-400">Loading users...</div>;

  return (
    <div>
      <h1 className="text-3xl font-display text-gold-400 mb-8">User Management</h1>
      
      <div className="glass-gold rounded-2xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-gold-900/40 border-b border-gold-600/30">
                <th className="p-4 text-gold-200 font-semibold text-sm uppercase tracking-wider">User</th>
                <th className="p-4 text-gold-200 font-semibold text-sm uppercase tracking-wider">Email</th>
                <th className="p-4 text-gold-200 font-semibold text-sm uppercase tracking-wider">Role</th>
                <th className="p-4 text-gold-200 font-semibold text-sm uppercase tracking-wider">Joined</th>
                <th className="p-4 text-gold-200 font-semibold text-sm uppercase tracking-wider text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gold-600/10">
              {users.map((user) => (
                <tr key={user.id} className="hover:bg-gold-900/20 transition-colors">
                  <td className="p-4">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-surface border border-gold-600/30 flex items-center justify-center">
                        <UserIcon className="w-4 h-4 text-gold-400" />
                      </div>
                      <span className="font-medium text-gold-100">{user.name || 'Unknown'}</span>
                    </div>
                  </td>
                  <td className="p-4 text-gold-100/70">{user.email}</td>
                  <td className="p-4">
                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                      user.role === 'admin' 
                        ? 'bg-gold-500/20 text-gold-400 border border-gold-500/30' 
                        : 'bg-surface text-gold-100/70 border border-gold-600/30'
                    }`}>
                      {user.role}
                    </span>
                  </td>
                  <td className="p-4 text-gold-100/50 text-sm">
                    {new Date(user.createdAt).toLocaleDateString()}
                  </td>
                  <td className="p-4 text-right">
                    {user.email !== 'wndjdj12@gmail.com' && (
                      user.role === 'admin' ? (
                        <button 
                          onClick={() => handleRoleChange(user.id, 'user')}
                          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-red-950/30 text-red-400 hover:bg-red-900/50 transition-colors text-sm border border-red-900/50"
                        >
                          <ShieldOff className="w-4 h-4" /> Revoke Admin
                        </button>
                      ) : (
                        <button 
                          onClick={() => handleRoleChange(user.id, 'admin')}
                          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-gold-900/30 text-gold-400 hover:bg-gold-800/50 transition-colors text-sm border border-gold-600/30"
                        >
                          <Shield className="w-4 h-4" /> Grant Admin
                        </button>
                      )
                    )}
                    {user.email === 'wndjdj12@gmail.com' && (
                      <span className="text-xs text-gold-600 italic">Permanent Admin</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
