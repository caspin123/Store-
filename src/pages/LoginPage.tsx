import { useState } from 'react';
import { useAuthStore } from '../store';
import toast from 'react-hot-toast';

export default function LoginPage({ onNavigate }: { onNavigate: (page: string) => void }) {
  const { setUser } = useAuthStore();
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    
    try {
      const res = await fetch('/api/auth/mock-login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ 
          email, 
          name: name || email.split('@')[0],
          image: `https://api.dicebear.com/7.x/avataaars/svg?seed=${email}`
        })
      });

      if (res.ok) {
        const data = await res.json();
        setUser(data.user);
        toast.success('Successfully signed in!', {
          style: { background: '#1a1500', color: '#ffd700', border: '1px solid rgba(184,134,11,0.3)' }
        });
        onNavigate('home');
      } else {
        toast.error('Login failed');
      }
    } catch (error) {
      toast.error('An error occurred');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-[80vh] flex items-center justify-center px-4">
      <div className="w-full max-w-md glass-gold p-8 rounded-3xl relative overflow-hidden">
        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-transparent via-gold-400 to-transparent"></div>
        
        <div className="text-center mb-8">
          <h1 className="font-display text-4xl text-gold-400 mb-2 text-glow-gold">Welcome Back</h1>
          <p className="text-gold-100/60">Sign in to your Dahabstore account</p>
        </div>

        <form onSubmit={handleLogin} className="space-y-6">
          <div>
            <label className="block text-sm font-medium text-gold-200 mb-2">Email Address</label>
            <input 
              type="email" 
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full bg-gold-900/30 border border-gold-600/50 rounded-xl px-4 py-3 text-gold-100 placeholder-gold-100/30 focus:outline-none focus:border-gold-400 focus:ring-1 focus:ring-gold-400 transition-all"
              placeholder="you@example.com"
            />
          </div>
          
          <div>
            <label className="block text-sm font-medium text-gold-200 mb-2">Full Name (Optional)</label>
            <input 
              type="text" 
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full bg-gold-900/30 border border-gold-600/50 rounded-xl px-4 py-3 text-gold-100 placeholder-gold-100/30 focus:outline-none focus:border-gold-400 focus:ring-1 focus:ring-gold-400 transition-all"
              placeholder="John Doe"
            />
          </div>

          <button 
            type="submit"
            disabled={loading}
            className="w-full py-4 rounded-xl bg-gold-500 text-black font-bold uppercase tracking-wider hover:bg-gold-400 transition-colors disabled:opacity-50 box-glow-gold mt-4"
          >
            {loading ? 'Signing In...' : 'Sign In with Google (Mock)'}
          </button>
          
          <p className="text-xs text-center text-gold-100/40 mt-4">
            Note: This is a mock login for the preview environment. Enter wndjdj12@gmail.com to get Admin access.
          </p>
        </form>
      </div>
    </div>
  );
}
